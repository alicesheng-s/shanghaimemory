package com.shanghai.memory.data.llm

import com.shanghai.memory.data.AppSettings
import com.shanghai.memory.data.model.ThinkingStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // 流式响应可能长时间静默，读超时给足
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val quickClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/** 一轮对话。role 取 "user" 或 "assistant"。 */
data class Turn(val role: String, val content: String)

/**
 * OpenAI 兼容的流式对话客户端。
 *
 * 关键点：DeepSeek V4 与 GLM-5.3 默认开启思考模式，会显著推迟首字时间，
 * 因此这里按服务商协议显式关闭思考，保证「首字 ≤3s」的非功能指标。
 */
class ChatClient(private val http: OkHttpClient = Http.client) {

    fun stream(
        settings: AppSettings,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 1400,
        jsonMode: Boolean = false,
        history: List<Turn> = emptyList(),
        onReasoning: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val full = attempt(
            settings = settings,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            maxTokens = maxTokens,
            jsonMode = jsonMode,
            disableThinking = true,
            history = history,
            onReasoning = onReasoning
        )
        // 有些兼容端点不认 thinking / reasoning_effort，退回不带该字段重试一次
        val result = if (full != null) full else attempt(
            settings = settings,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            maxTokens = maxTokens,
            jsonMode = jsonMode,
            disableThinking = false,
            history = history,
            onReasoning = onReasoning
        )
        if (result == null) throw IOException("请求被服务端拒绝，请检查模型名与 Key 是否匹配")
        emitAll(result)
    }.flowOn(Dispatchers.IO)

    /** 返回 null 表示首次握手就失败，交由上层决定是否降级重试 */
    private suspend fun attempt(
        settings: AppSettings,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        jsonMode: Boolean,
        disableThinking: Boolean,
        history: List<Turn> = emptyList(),
        onReasoning: ((String) -> Unit)? = null
    ): Flow<String>? {
        val payload = JSONObject().apply {
            put("model", settings.model)
            put("stream", true)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                history.forEach { turn ->
                    put(JSONObject().put("role", turn.role).put("content", turn.content))
                }
                put(JSONObject().put("role", "user").put("content", userPrompt))
            })
            put("max_tokens", maxTokens)
            put("temperature", settings.temperature)
            if (disableThinking) {
                when (settings.provider.thinkingStyle) {
                    ThinkingStyle.DEEPSEEK_THINKING ->
                        put("thinking", JSONObject().put("type", "disabled"))

                    ThinkingStyle.GLM_REASONING_EFFORT ->
                        put("reasoning_effort", "low")

                    ThinkingStyle.NONE -> Unit
                }
            }
            if (jsonMode) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }

        val request = Request.Builder()
            .url(settings.chatEndpoint)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("网络连接失败：${e.message}", e)
        }

        if (!response.isSuccessful) {
            val code = response.code
            val body = runCatching { response.body?.string().orEmpty() }.getOrElse { "" }
            response.close()
            // 4xx 且是参数类错误时，返回 null 让上层去掉思考开关重试
            if (disableThinking && (code == 400 || code == 422)) return null
            throw IOException("HTTP $code：${body.take(300)}")
        }

        val sink = response.body ?: run {
            response.close()
            throw IOException("响应体为空")
        }

        return flow {
            sink.use { body ->
                val source = body.source()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty() || line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    // 正文与思考内容分两路：思考内容只回调出去做进度展示，不进正文
                    val parsed = runCatching {
                        val d = JSONObject(data)
                            .optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("delta")
                        val content = d?.optString("content").orEmpty()
                        val reasoning = d?.optString("reasoning_content").orEmpty()
                            .ifEmpty { d?.optString("reasoning").orEmpty() }
                        content to reasoning
                    }.getOrElse { "" to "" }
                    if (parsed.second.isNotEmpty()) onReasoning?.invoke(parsed.second)
                    if (parsed.first.isNotEmpty()) emit(parsed.first)
                }
            }
        }
    }

    /** 一次性取完整回复，用于连通性测试与 JSON 生成 */
    suspend fun complete(
        settings: AppSettings,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 2000,
        jsonMode: Boolean = false
    ): String {
        val sb = StringBuilder()
        stream(settings, systemPrompt, userPrompt, maxTokens, jsonMode).collect { sb.append(it) }
        return sb.toString()
    }
}
