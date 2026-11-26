package com.shanghai.memory.data.llm

import com.shanghai.memory.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** 一家服务商通过 GET /models 暴露出来的单个模型。 */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val contextWindow: Int? = null,
    val effortDefault: String? = null,
    val modalities: String = ""
) {
    /** 列表里的两行文案：展示名 + 上下文/能力摘要。 */
    val subtitle: String
        get() {
            val en = com.shanghai.memory.ui.theme.ThemeState.isEnglish
            return buildList {
                contextWindow?.let { if (it > 0) add(if (en) "Context ${formatK(it)}" else "上下文 ${formatK(it)}") }
                effortDefault?.let { if (it.isNotBlank()) add(if (en) "Reasoning $it" else "默认思考 $it") }
                if (modalities.isNotBlank()) add(modalities)
            }.joinToString(" · ")
        }
}

private fun formatK(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}

/**
 * 自动获取可用模型。
 *
 * 四家实测都实现了 OpenAI 风格的 GET /models（无 Key 时返回 401，说明端点存在）：
 *   DeepSeek  https://api.deepseek.com/models
 *   智谱       https://open.bigmodel.cn/api/paas/v4/models
 *   Kimi      https://api.moonshot.cn/v1/models
 * 自定义端点若同样兼容，一并可用。
 *
 * 只认 data[] 与 models[] 两种数组形态，其余字段一律忽略——各家的附加字段差异太大，
 * 解析失败也不能影响取到 id。
 */
object ModelCatalog {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun list(
        settings: AppSettings,
        http: OkHttpClient = Http.quickClient
    ): List<ModelInfo> = withContext(Dispatchers.IO) {
        val en = com.shanghai.memory.ui.theme.ThemeState.isEnglish
        if (settings.apiKey.isBlank()) throw IOException(if (en) "Enter API Key first" else "请先填 API Key")
        val endpoint = settings.modelsEndpoint

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        val body = http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(
                    when (response.code) {
                        401, 403 -> if (en) "Invalid Key or no access to /models" else "Key 无效或无权访问模型列表"
                        404 -> if (en) "No /models endpoint here—enter model name manually" else "该端点没有 /models 接口，请手动填写模型名"
                        else -> "HTTP ${response.code}: ${text.take(120)}"
                    }
                )
            }
            text
        }

        val root = json.parseToJsonElement(body).jsonObject
        val array = root["data"] as? JsonArray ?: root["models"] as? JsonArray

        val list = array?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.text("id") ?: obj.text("model") ?: return@mapNotNull null
            val name = obj.text("name") ?: id
            val context = obj.text("context_window")?.toIntOrNull()
            val effort = (obj["effort"] as? JsonObject)?.text("default_level")
            val modalities = buildString {
                val input = obj["input_modalities"] as? JsonArray
                val output = obj["output_modalities"] as? JsonArray
                if (input != null && output != null) {
                    val visual = input.any {
                        (it as? JsonPrimitive)?.content?.contains("image", true) == true
                    }
                    if (visual) append(if (en) "Vision" else "支持图片输入")
                }
            }
            ModelInfo(id, name, context, effort, modalities)
        }.orEmpty()

        if (list.isEmpty()) throw IOException(if (en) "Endpoint responded, but no models found" else "接口可用，但没有解析到任何模型")
        list.sortedBy { it.id }
    }
}

/**
 * JsonNull 也是 JsonPrimitive，直接取 content 会得到字符串 "null"：
 * 于是模型列表里冒出名为「null」的条目。这里把 null 与空串一并当缺失。
 */
private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }
