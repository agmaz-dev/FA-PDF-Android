// FA KI – schlanke JNI-Brücke zu llama.cpp.
// Ein Modell wird geladen; jede Anfrage bekommt den kompletten Chatverlauf und startet mit leerem Speicher.

#include <jni.h>
#include <atomic>
#include <string>
#include <vector>
#include <unistd.h>
#include <algorithm>
#include <csignal>
#include <cstring>
#include <fcntl.h>

#include "logging.h"
#include "chat.h"
#include "common.h"
#include "sampling.h"
#include "llama.h"
#include "ggml-backend.h"

static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static common_chat_templates_ptr g_tmpls;
static int g_n_ctx = 8192;
static std::atomic<bool> g_abort{false};
// Fortschritt für die Anzeige: Phase 0 = frei, 1 = liest Eingabe, 2 = schreibt Antwort
static std::atomic<int> g_phase{0}, g_in_done{0}, g_in_total{0}, g_out{0};
static std::atomic<long long> g_t_start{0}, g_t_gen{0}, g_t_end{0};
static long long now_ms() { return ggml_time_us() / 1000; }

/** "phase;gelesen;gesamt;geschrieben;ms_lesen;ms_schreiben" */
extern "C" JNIEXPORT jstring JNICALL
Java_de_faki_Native_progress(JNIEnv *env, jclass) {
    const long long t = now_ms();
    const int ph = g_phase;
    const long long read_ms = (ph == 1 ? t : g_t_gen.load()) - g_t_start;
    const long long gen_ms = ph == 2 ? t - g_t_gen : (ph == 0 && g_t_end > g_t_gen ? g_t_end - g_t_gen : 0);
    std::string s = std::to_string(ph) + ";" + std::to_string(g_in_done.load()) + ";" + std::to_string(g_in_total.load()) + ";" +
                    std::to_string(g_out.load()) + ";" + std::to_string(read_ms) + ";" + std::to_string(gen_ms);
    return env->NewStringUTF(s.c_str());
}

static std::string jstr_early(JNIEnv *env, jstring s) {
    if (!s) return "";
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string r(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return r;
}

static constexpr int BATCH = 512;
static constexpr int UBATCH = 256;

// ---------- Absturz-Protokoll: letzte Meldungen + Signal in eine Datei schreiben ----------
static char g_crash_path[512] = {0};
static constexpr int RING = 24;
static char g_ring[RING][240];
static std::atomic<int> g_ring_pos{0};

static void ring_add(const char *text) {
    const int p = g_ring_pos.fetch_add(1) % RING;
    strncpy(g_ring[p], text ? text : "", sizeof(g_ring[p]) - 1);
    g_ring[p][sizeof(g_ring[p]) - 1] = 0;
}

static void wr(int fd, const char *s) { if (s) { ssize_t r = write(fd, s, strlen(s)); (void) r; } }

static void write_crash(const char *what) {
    if (!g_crash_path[0]) return;
    int fd = open(g_crash_path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) return;
    char buf[200];
    snprintf(buf, sizeof(buf), "\nPhase %d · gelesen %d/%d · geschrieben %d\n", g_phase.load(), g_in_done.load(), g_in_total.load(), g_out.load());
    wr(fd, what); wr(fd, buf);
    wr(fd, "Letzte Meldungen:\n");
    const int end = g_ring_pos.load();
    for (int k = std::max(0, end - RING); k < end; k++) { wr(fd, g_ring[k % RING]); const size_t l = strlen(g_ring[k % RING]); if (!l || g_ring[k % RING][l - 1] != '\n') wr(fd, "\n"); }
    close(fd);
}

static void on_ggml_abort(const char *msg) { write_crash(msg ? msg : "ggml abort"); }

static struct sigaction g_old[32];
static void on_signal(int sig, siginfo_t *info, void *uc) {
    char b[80];
    snprintf(b, sizeof(b), "Signal %d (%s)", sig, sig == SIGSEGV ? "Speicherzugriff" : sig == SIGABRT ? "Abbruch" : sig == SIGBUS ? "Bus" : sig == SIGILL ? "Befehl" : "andere");
    // Bei SIGABRT nach ggml-Abbruch nicht überschreiben
    if (!(sig == SIGABRT && access(g_crash_path, F_OK) == 0)) write_crash(b);
    sigaction(sig, &g_old[sig], nullptr);
    raise(sig);
}

static void log_cb(enum ggml_log_level level, const char *text, void *user) {
    if (level >= GGML_LOG_LEVEL_INFO || level == GGML_LOG_LEVEL_CONT) ring_add(text);
    aichat_android_log_callback(level, text, user);
}

extern "C" JNIEXPORT void JNICALL
Java_de_faki_Native_setCrashFile(JNIEnv *env, jclass, jstring jpath) {
    std::string p = jstr_early(env, jpath);
    strncpy(g_crash_path, p.c_str(), sizeof(g_crash_path) - 1);
    ggml_set_abort_callback(on_ggml_abort);
    struct sigaction sa{};
    sa.sa_sigaction = on_signal;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);
    for (int s : {SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE}) sigaction(s, &sa, &g_old[s]);
}

static std::string jstr(JNIEnv *env, jstring s) {
    if (!s) return "";
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string r(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return r;
}

// Java-Strings dürfen nur gültiges (modifiziertes) UTF-8 enthalten – sonst stürzt NewStringUTF ab.
static jstring to_jstring(JNIEnv *env, const std::string &s) {
    std::string clean;
    clean.reserve(s.size());
    const auto *b = (const unsigned char *) s.data();
    size_t i = 0, n = s.size();
    while (i < n) {
        unsigned char c = b[i];
        int len = c < 0x80 ? 1 : (c & 0xE0) == 0xC0 ? 2 : (c & 0xF0) == 0xE0 ? 3 : (c & 0xF8) == 0xF0 ? 4 : 0;
        if (len == 0 || i + len > n) { i++; continue; }
        bool ok = true;
        for (int k = 1; k < len; k++) if ((b[i + k] & 0xC0) != 0x80) { ok = false; break; }
        if (!ok) { i++; continue; }
        if (c == 0) { i++; continue; }
        clean.append(s, i, len);
        i += len;
    }
    // 4-Byte-Zeichen (Emojis) als Ersatzzeichen, da modifiziertes UTF-8 sie nicht direkt kennt
    std::string out;
    out.reserve(clean.size());
    for (size_t k = 0; k < clean.size();) {
        unsigned char c = (unsigned char) clean[k];
        if ((c & 0xF8) == 0xF0) { out += "\xEF\xBF\xBD"; k += 4; }
        else { out += clean[k]; k++; }
    }
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_de_faki_Native_nativeInit(JNIEnv *env, jclass, jstring jlibdir) {
    llama_log_set(log_cb, nullptr);
    std::string dir = jstr(env, jlibdir);
    ggml_backend_load_all_from_path(dir.c_str());
    llama_backend_init();
}

/** Gefundene Rechenwerke, z. B. "CPU; GPUOpenCL (QUALCOMM Adreno(TM) 830)". */
extern "C" JNIEXPORT jstring JNICALL
Java_de_faki_Native_devices(JNIEnv *env, jclass) {
    std::string out;
    for (size_t i = 0; i < ggml_backend_dev_count(); i++) {
        ggml_backend_dev_t d = ggml_backend_dev_get(i);
        if (!out.empty()) out += "; ";
        out += ggml_backend_dev_name(d);
        const char *desc = ggml_backend_dev_description(d);
        if (desc && *desc) { out += " ("; out += desc; out += ")"; }
        if (ggml_backend_dev_type(d) == GGML_BACKEND_DEVICE_TYPE_GPU) out += " [GPU]";
    }
    return to_jstring(env, out);
}

extern "C" JNIEXPORT jint JNICALL
Java_de_faki_Native_load(JNIEnv *env, jclass, jstring jpath, jint n_ctx, jint n_gpu_layers) {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_tmpls.reset();

    std::string path = jstr(env, jpath);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = n_gpu_layers;
    g_model = llama_model_load_from_file(path.c_str(), mp);
    if (!g_model) return 1;

    const int cores = (int) sysconf(_SC_NPROCESSORS_ONLN);
    const int threads = std::max(2, std::min(6, cores - 2));
    llama_context_params cp = llama_context_default_params();
    g_n_ctx = std::min((int) n_ctx, (int) llama_model_n_ctx_train(g_model));
    cp.n_ctx = g_n_ctx;
    cp.n_batch = BATCH;
    cp.n_ubatch = UBATCH;
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) { llama_model_free(g_model); g_model = nullptr; return 2; }

    g_tmpls = common_chat_templates_init(g_model, "");
    LOGi("Modell geladen: ctx=%d threads=%d", g_n_ctx, threads);
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_de_faki_Native_abort(JNIEnv *, jclass) { g_abort = true; }

extern "C" JNIEXPORT void JNICALL
Java_de_faki_Native_unload(JNIEnv *, jclass) {
    g_tmpls.reset();
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

extern "C" JNIEXPORT jstring JNICALL
Java_de_faki_Native_describe(JNIEnv *env, jclass) {
    if (!g_model) return env->NewStringUTF("");
    char buf[256];
    llama_model_desc(g_model, buf, sizeof(buf));
    return env->NewStringUTF(buf);
}

/** Antwort auf einen Chatverlauf. Fehler werden als Text mit Präfix "\u0001FEHLER:" zurückgegeben. */
extern "C" JNIEXPORT jstring JNICALL
Java_de_faki_Native_complete(JNIEnv *env, jclass, jobjectArray roles, jobjectArray contents, jint max_tokens, jfloat temp) {
    if (!g_model || !g_ctx) return env->NewStringUTF("\x01" "FEHLER:Kein Modell geladen.");
    g_abort = false;

    common_chat_templates_inputs in;
    const jsize n = env->GetArrayLength(roles);
    for (jsize i = 0; i < n; i++) {
        common_chat_msg m;
        m.role = jstr(env, (jstring) env->GetObjectArrayElement(roles, i));
        m.content = jstr(env, (jstring) env->GetObjectArrayElement(contents, i));
        in.messages.push_back(std::move(m));
    }
    in.add_generation_prompt = true;
    in.use_jinja = true;
    in.enable_thinking = false;

    std::string prompt;
    std::vector<std::string> stops;
    try {
        common_chat_params p = common_chat_templates_apply(g_tmpls.get(), in);
        prompt = p.prompt.str();
        stops = p.additional_stops;
    } catch (const std::exception &e) {
        // Fallback: einfache Vorlage ohne Jinja
        in.use_jinja = false;
        try {
            common_chat_params p = common_chat_templates_apply(g_tmpls.get(), in);
            prompt = p.prompt.str();
        } catch (const std::exception &e2) {
            return to_jstring(env, std::string("\x01" "FEHLER:Chat-Vorlage: ") + e2.what());
        }
    }

    llama_memory_clear(llama_get_memory(g_ctx), true);

    std::vector<llama_token> toks = common_tokenize(g_ctx, prompt, false, true);
    const int budget = g_n_ctx - (int) max_tokens - 8;
    if (budget < 64) return env->NewStringUTF("\x01" "FEHLER:Kontext zu klein.");
    if ((int) toks.size() > budget) {
        // Anfang (Vorlage, Systemtext) und Ende (eigentliche Frage) behalten, Mitte kürzen
        const int head = std::min(512, budget / 4);
        std::vector<llama_token> cut(toks.begin(), toks.begin() + head);
        cut.insert(cut.end(), toks.end() - (budget - head), toks.end());
        toks.swap(cut);
        LOGw("Eingabe gekürzt auf %d Tokens", (int) toks.size());
    }

    g_in_total = (int) toks.size(); g_in_done = 0; g_out = 0;
    g_t_start = now_ms(); g_t_gen = g_t_start.load(); g_phase = 1;
    common_batch batch(g_ctx);
    for (int i = 0; i < (int) toks.size(); i += BATCH) {
        const int m = std::min(BATCH, (int) toks.size() - i);
        batch.clear();
        for (int j = 0; j < m; j++) {
            const bool last = (i + j) == (int) toks.size() - 1;
            batch.add(toks[i + j], i + j, 0, last);
        }
        if (llama_process(g_ctx, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0)
            { g_phase = 0; return env->NewStringUTF("\x01" "FEHLER:Verarbeitung der Eingabe fehlgeschlagen."); }
        g_in_done = i + m;
        if (g_abort) { g_phase = 0; return env->NewStringUTF("\x01" "FEHLER:Abgebrochen."); }
    }
    g_t_gen = now_ms(); g_phase = 2;

    common_params_sampling sp;
    sp.temp = temp;
    common_sampler *smpl = common_sampler_init(g_model, sp);
    const llama_vocab *vocab = llama_model_get_vocab(g_model);

    std::string out;
    int pos = (int) toks.size();
    for (int k = 0; k < max_tokens && pos < g_n_ctx - 1; k++) {
        if (g_abort) break;
        const llama_token id = common_sampler_sample(smpl, g_ctx, -1);
        common_sampler_accept(smpl, id, true);
        if (llama_vocab_is_eog(vocab, id)) break;
        g_out++;
        out += common_token_to_piece(g_ctx, id, false);
        bool stop = false;
        for (const auto &s : stops) {
            if (!s.empty() && out.size() >= s.size() && out.compare(out.size() - s.size(), s.size(), s) == 0) {
                out.erase(out.size() - s.size());
                stop = true;
                break;
            }
        }
        if (stop) break;
        batch.clear();
        batch.add(id, pos++, 0, true);
        if (llama_process(g_ctx, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) break;
    }
    common_sampler_free(smpl);
    g_t_end = now_ms(); g_phase = 0;
    return to_jstring(env, out);
}
