package com.shanghai.memory.data.llm

import com.shanghai.memory.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
        get() = buildList {
            contextWindow?.let { if (it > 0) add("上下文 ${formatK(it)}") }
            effortDefault?.let { if (it.isNotBlank()) add("默认思考 $it") }
            if (modalities.isNotBlank()) add(modalities)
        }.joinToString(" · ")
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
        if (settings.apiKey.isBlank()) throw IOException("先填 API Key")
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
                        401, 403 -> "Key 无效或无权访问模型列表"
                        404 -> "该端点没有 /models 接口，请手动填写模型名"
                        else -> "HTTP ${response.code}：${text.take(120)}"
                    }
                )
            }
            text
        }

        val root = json.parseToJsonElement(body).jsonObject
        val array = root["data"] as? JsonArray ?: root["models"] as? JsonArray

        val list = array?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.content
                ?: obj["model"]?.jsonPrimitive?.content
                ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: id
            val context = obj["context_window"]?.jsonPrimitive?.content?.toIntOrNull()
            val effort = (obj["effort"] as? JsonObject)
                ?.get("default_level")?.jsonPrimitive?.content
            val modalities = buildString {
                val input = obj["input_modalities"] as? JsonArray
                val output = obj["output_modalities"] as? JsonArray
                if (input != null && output != null) {
                    val visual = input.any { it.jsonPrimitive.content.contains("image", true) }
                    if (visual) append("支持图片输入")
                }
            }
            ModelInfo(id, name, context, effort, modalities)
        }.orEmpty()

        if (list.isEmpty()) throw IOException("接口可用，但没有解析到任何模型")
        list.sortedBy { it.id }
    }
}
