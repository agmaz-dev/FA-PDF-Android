package de.faki

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * OpenAI-kompatible Adresse nur auf diesem Gerät: http://127.0.0.1:8080/v1
 * Andere Apps (FA PDF, Jarvis, FA Mail) tragen sie wie Groq als „Eigene Adresse“ ein.
 */
class KiServer(private val context: Context) : NanoHTTPD("127.0.0.1", PORT) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            val path = session.uri.trimEnd('/')
            when {
                session.method == Method.OPTIONS -> cors(NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain", ""))
                session.method == Method.GET && (path == "" || path == "/v1") ->
                    text("FA KI läuft. Adresse für Apps: $BASE_URL")
                session.method == Method.GET && path.endsWith("/models") -> json(models())
                session.method == Method.POST && path.endsWith("/chat/completions") -> chat(session)
                else -> error(Response.Status.NOT_FOUND, "Unbekannter Pfad: ${session.uri}")
            }
        } catch (e: KiException) {
            error(Response.Status.INTERNAL_ERROR, e.message ?: "KI nicht bereit")
        } catch (e: Exception) {
            error(Response.Status.INTERNAL_ERROR, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun models(): JSONObject {
        val data = JSONArray()
        Models.all.filter { Models.isDownloaded(context, it) }.forEach {
            data.put(JSONObject().put("id", it.id).put("object", "model").put("owned_by", "fa-ki"))
        }
        // „lokal“ steht immer für das gerade eingeschaltete Modell
        data.put(JSONObject().put("id", "lokal").put("object", "model").put("owned_by", "fa-ki"))
        return JSONObject().put("object", "list").put("data", data)
    }

    private fun readBody(session: IHTTPSession): String {
        val len = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (len <= 0) return ""
        val buf = ByteArray(len)
        var read = 0
        val input = session.inputStream
        while (read < len) {
            val r = input.read(buf, read, len - read)
            if (r < 0) break
            read += r
        }
        return String(buf, 0, read, Charsets.UTF_8)
    }

    private fun contentText(v: Any?): String = when (v) {
        is String -> v
        is JSONArray -> buildString {
            for (i in 0 until v.length()) {
                val part = v.optJSONObject(i) ?: continue
                if (part.optString("type") == "text") { if (isNotEmpty()) append("\n"); append(part.optString("text")) }
            }
        }
        else -> ""
    }

    private fun chat(session: IHTTPSession): Response {
        val req = JSONObject(readBody(session))
        val arr = req.optJSONArray("messages") ?: return error(Response.Status.BAD_REQUEST, "messages fehlt")
        val msgs = ArrayList<Msg>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val role = when (o.optString("role")) { "system", "developer" -> "system"; "assistant" -> "assistant"; else -> "user" }
            msgs += Msg(role, contentText(o.opt("content")))
        }
        val maxTokens = when {
            req.has("max_tokens") -> req.optInt("max_tokens")
            req.has("max_completion_tokens") -> req.optInt("max_completion_tokens")
            else -> 1024
        }.coerceIn(16, MAX_ANSWER_TOKENS)
        val temp = req.optDouble("temperature", 0.3).toFloat().coerceIn(0f, 2f)
        val stream = req.optBoolean("stream", false)

        // Anfragen anderer Apps mit hoher Priorität rechnen, auch wenn FA KI im Hintergrund ist
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        val hint = "Antworte knapp und auf den Punkt."
        // Systemtext muss am Anfang stehen (sonst lehnen manche Chat-Vorlagen die Anfrage ab)
        val withHint = if (msgs.firstOrNull()?.role == "system") listOf(msgs[0].copy(content = msgs[0].content + "\n\n" + hint)) + msgs.drop(1)
            else listOf(Msg("system", hint)) + msgs
        val answer = Engine.complete(context, withHint, maxTokens, temp)
        val model = Settings.active(context)?.id ?: "lokal"
        val id = "faki-" + System.currentTimeMillis()
        val created = System.currentTimeMillis() / 1000

        if (stream) {
            // Ganze Antwort als ein Stück senden (genügt Programmen, die Streaming erwarten)
            val chunk = JSONObject().put("id", id).put("object", "chat.completion.chunk").put("created", created).put("model", model)
                .put("choices", JSONArray().put(JSONObject().put("index", 0)
                    .put("delta", JSONObject().put("role", "assistant").put("content", answer)).put("finish_reason", JSONObject.NULL)))
            val end = JSONObject().put("id", id).put("object", "chat.completion.chunk").put("created", created).put("model", model)
                .put("choices", JSONArray().put(JSONObject().put("index", 0).put("delta", JSONObject()).put("finish_reason", "stop")))
            val body = "data: $chunk\n\ndata: $end\n\ndata: [DONE]\n\n"
            val bytes = body.toByteArray(Charsets.UTF_8)
            return cors(NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/event-stream; charset=utf-8", ByteArrayInputStream(bytes), bytes.size.toLong()))
        }

        val res = JSONObject().put("id", id).put("object", "chat.completion").put("created", created).put("model", model)
            .put("choices", JSONArray().put(JSONObject().put("index", 0)
                .put("message", JSONObject().put("role", "assistant").put("content", answer)).put("finish_reason", "stop")))
            .put("usage", JSONObject().put("prompt_tokens", 0).put("completion_tokens", 0).put("total_tokens", 0))
        return json(res)
    }

    private fun json(o: JSONObject): Response {
        val bytes = o.toString().toByteArray(Charsets.UTF_8)
        return cors(NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", ByteArrayInputStream(bytes), bytes.size.toLong()))
    }

    private fun text(s: String): Response {
        val bytes = s.toByteArray(Charsets.UTF_8)
        return cors(NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", ByteArrayInputStream(bytes), bytes.size.toLong()))
    }

    private fun error(status: Response.Status, message: String): Response {
        val bytes = JSONObject().put("error", JSONObject().put("message", message).put("type", "fa_ki_error")).toString().toByteArray(Charsets.UTF_8)
        return cors(NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", ByteArrayInputStream(bytes), bytes.size.toLong()))
    }

    private fun cors(r: Response): Response = r.apply {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Headers", "*")
        addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    }

    companion object {
        const val PORT = 8080
        /** Obergrenze für eine Antwort auf dem Handy: reicht für eine ausführliche Zusammenfassung, ohne minutenlang zu rechnen. */
        const val MAX_ANSWER_TOKENS = 700
        const val BASE_URL = "http://127.0.0.1:8080/v1"
    }
}
