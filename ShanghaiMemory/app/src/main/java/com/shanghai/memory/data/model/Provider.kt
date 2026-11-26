package com.shanghai.memory.data.model

/** 联网检索能力类型：不同服务商的搜索接口协议不同 */
enum class SearchKind {
    /** DeepSeek 官方 Search API：POST /v1/tools/search */
    DEEPSEEK_TOOLS,

    /** 智谱 Web Search API：POST /api/paas/v4/web_search */
    ZHIPU_WEB,

    /** 无检索能力，只能纯对话生成 */
    NONE,

    /** 自定义：用户自行填写检索端点，按 OpenAI 风格 POST {query} 处理 */
    CUSTOM
}

/** 关闭思考模式所需的协议差异 */
enum class ThinkingStyle {
    /** DeepSeek V4 系列：thinking.type = disabled */
    DEEPSEEK_THINKING,

    /** 智谱 GLM：reasoning_effort = low */
    GLM_REASONING_EFFORT,

    /** 无此概念 */
    NONE
}

/**
 * 服务商预设。baseUrl 统一存放 OpenAI 兼容的 chat 前缀，
 * 代码里拼接 "$baseUrl/chat/completions"。
 */
data class ProviderProfile(
    val id: String,
    private val displayNameZh: String,
    private val displayNameEn: String,
    val chatBaseUrl: String,
    val defaultModel: String,
    val modelOptions: List<String>,
    val searchKind: SearchKind,
    val thinkingStyle: ThinkingStyle,
    val isCustom: Boolean = false
) {
    val displayName: String
        get() = if (com.shanghai.memory.ui.theme.ThemeState.isEnglish) displayNameEn else displayNameZh
    val chatEndpoint: String get() = "${chatBaseUrl.trimEnd('/')}/chat/completions"
    val hasWebSearch: Boolean get() = searchKind != SearchKind.NONE
}

object Providers {

    val DEEPSEEK = ProviderProfile(
        id = "deepseek",
        displayNameZh = "DeepSeek 官方",
        displayNameEn = "DeepSeek Official",
        chatBaseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-flash",
        modelOptions = listOf("deepseek-flash", "deepseek-v4-pro"),
        searchKind = SearchKind.DEEPSEEK_TOOLS,
        thinkingStyle = ThinkingStyle.DEEPSEEK_THINKING
    )

    val ZHIPU = ProviderProfile(
        id = "zhipu",
        displayNameZh = "智谱 GLM",
        displayNameEn = "Zhipu GLM",
        chatBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultModel = "glm-4-flash",
        modelOptions = listOf("glm-4-flash", "glm-5.3-flash", "glm-5.3"),
        searchKind = SearchKind.ZHIPU_WEB,
        thinkingStyle = ThinkingStyle.GLM_REASONING_EFFORT
    )

    val KIMI = ProviderProfile(
        id = "kimi",
        displayNameZh = "Kimi / Moonshot",
        displayNameEn = "Kimi / Moonshot",
        chatBaseUrl = "https://api.moonshot.cn/v1",
        defaultModel = "moonshot-v1-32k",
        modelOptions = listOf("moonshot-v1-8k", "moonshot-v1-32k", "kimi-k2-turbo-preview"),
        searchKind = SearchKind.NONE,
        thinkingStyle = ThinkingStyle.NONE
    )

    val CUSTOM = ProviderProfile(
        id = "custom",
        displayNameZh = "自定义（OpenAI 兼容）",
        displayNameEn = "Custom (OpenAI Compatible)",
        chatBaseUrl = "https://api.example.com/v1",
        defaultModel = "",
        modelOptions = emptyList(),
        searchKind = SearchKind.NONE,
        thinkingStyle = ThinkingStyle.NONE,
        isCustom = true
    )

    val presets: List<ProviderProfile> = listOf(DEEPSEEK, ZHIPU, KIMI, CUSTOM)

    fun byId(id: String?): ProviderProfile = presets.firstOrNull { it.id == id } ?: DEEPSEEK
}
