// FA KI – schlanke JNI-Brücke zu llama.cpp.
// Ein Modell wird geladen; jede Anfrage bekommt den kompletten Chatverlauf und startet mit leerem Speicher.

#include <jni.h>
#include <atomic>
#include <string>
#include <vector>
#include <unistd.h>
#include <algorithm>

#include "logging.h"
#include "chat.h"
#include "common.h"
#include "sampling.h"
#include "llama.h"

static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static common_chat_templates_ptr g_tmpls;
static int g_n_ctx = 8192;
static std::atomic<bool> g_abort{false};

static constexpr int BATCH = 512;

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
Java_de_faki_Native_nativeInit(JNIEnv *, jclass) {
    llama_log_set(aichat_android_log_callback, nullptr);
    llama_backend_init();
}

extern "C" JNIEXPORT jint JNICALL
Java_de_faki_Native_load(JNIEnv *env, jclass, jstring jpath, jint n_ctx) {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_tmpls.reset();

    std::string path = jstr(env, jpath);
    llama_model_params mp = llama_model_default_params();
    g_model = llama_model_load_from_file(path.c_str(), mp);
    if (!g_model) return 1;

    const int cores = (int) sysconf(_SC_NPROCESSORS_ONLN);
    const int threads = std::max(2, std::min(6, cores - 2));
    llama_context_params cp = llama_context_default_params();
    g_n_ctx = std::min((int) n_ctx, (int) llama_model_n_ctx_train(g_model));
    cp.n_ctx = g_n_ctx;
    cp.n_batch = BATCH;
    cp.n_ubatch = BATCH;
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

    common_batch batch(g_ctx);
    for (int i = 0; i < (int) toks.size(); i += BATCH) {
        const int m = std::min(BATCH, (int) toks.size() - i);
        batch.clear();
        for (int j = 0; j < m; j++) {
            const bool last = (i + j) == (int) toks.size() - 1;
            batch.add(toks[i + j], i + j, 0, last);
        }
        if (llama_process(g_ctx, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0)
            return env->NewStringUTF("\x01" "FEHLER:Verarbeitung der Eingabe fehlgeschlagen.");
        if (g_abort) return env->NewStringUTF("\x01" "FEHLER:Abgebrochen.");
    }

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
    return to_jstring(env, out);
}
