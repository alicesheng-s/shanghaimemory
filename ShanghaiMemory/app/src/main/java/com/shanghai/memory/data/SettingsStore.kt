package com.shanghai.memory.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shanghai.memory.data.model.ProviderProfile
import com.shanghai.memory.data.model.Providers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

data class AppSettings(
    val providerId: String = Providers.DEEPSEEK.id,
    val apiKey: String = "",
    val model: String = Providers.DEEPSEEK.defaultModel,
    val baseUrl: String = Providers.DEEPSEEK.chatBaseUrl,
    val webSearchEnabled: Boolean = true,
    val customSearchUrl: String = "",
    val temperature: Float = 0.7f,
    val paletteId: String = "ink_gold",
    val ttsEnabled: Boolean = false,
    val ttsSpeed: Float = 1.0f,
    // 地标筛选：默认值与 0.3.3 写死时的行为一致，不动设置就等于没变
    val allowPublicInstitution: Boolean = true,
    val allowBusiness: Boolean = false,
    val strictOnly: Boolean = false,
    val extraKeywords: String = "",
    val expandParentContext: Boolean = true,
    // 地图服务与快捷入口
    val amapApiKey: String = "",
    val showQuickBar: Boolean = true,
    // 外观：亮暗、主色、圆角、字体
    val lightMode: Boolean = false,
    val followSystem: Boolean = true,
    val customAccent: String = "",
    val cornerDp: Int = 18,
    val surfaceAlpha: Int = 100,
    val fontId: String = "system_sans",
    val fontScale: Float = 1.0f,
    val importedFontPath: String = "",
    val onboarded: Boolean = false,
    val language: String = "zh"
) {
    val provider: ProviderProfile get() = Providers.byId(providerId)
    val hasKey: Boolean get() = apiKey.isNotBlank() && model.isNotBlank()
    val effectiveBaseUrl: String get() = baseUrl.ifBlank { provider.chatBaseUrl }
    val chatEndpoint: String get() = "${effectiveBaseUrl.trimEnd('/')}/chat/completions"
    val modelsEndpoint: String get() = "${effectiveBaseUrl.trimEnd('/')}/models"
    val canSearch: Boolean
        get() = webSearchEnabled && (provider.hasWebSearch || customSearchUrl.isNotBlank())
}

private val Context.settingsDataStore by preferencesDataStore(name = "shanghai_memory_settings")

class SettingsStore(private val context: Context) {

    private object Key {
        val providerId = stringPreferencesKey("provider_id")
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val baseUrl = stringPreferencesKey("base_url")
        val webSearch = booleanPreferencesKey("web_search_enabled")
        val customSearchUrl = stringPreferencesKey("custom_search_url")
        val temperature = stringPreferencesKey("temperature")
        val paletteId = stringPreferencesKey("palette_id")
        val ttsEnabled = booleanPreferencesKey("tts_enabled")
        val ttsSpeed = stringPreferencesKey("tts_speed")
        val allowPublicInstitution = booleanPreferencesKey("gate_public_institution")
        val allowBusiness = booleanPreferencesKey("gate_business")
        val strictOnly = booleanPreferencesKey("gate_strict_only")
        val extraKeywords = stringPreferencesKey("gate_extra_keywords")
        val expandParentContext = booleanPreferencesKey("gate_expand_parent")
        val amapApiKey = stringPreferencesKey("amap_api_key")
        val showQuickBar = booleanPreferencesKey("ui_show_quick_bar")
        val lightMode = booleanPreferencesKey("ui_light_mode")
        val followSystem = booleanPreferencesKey("ui_follow_system")
        val customAccent = stringPreferencesKey("ui_custom_accent")
        val cornerDp = stringPreferencesKey("ui_corner_dp")
    val surfaceAlpha = stringPreferencesKey("ui_surface_alpha")
        val fontId = stringPreferencesKey("ui_font_id")
        val fontScale = stringPreferencesKey("ui_font_scale")
        val importedFontPath = stringPreferencesKey("ui_imported_font_path")
        val onboarded = booleanPreferencesKey("onboarded")
        val language = stringPreferencesKey("ui_language")
        val customPresets = stringPreferencesKey("custom_presets_json")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        val providerId = p[Key.providerId] ?: Providers.DEEPSEEK.id
        val preset = Providers.byId(providerId)
        AppSettings(
            providerId = providerId,
            apiKey = p[Key.apiKey].orEmpty(),
            model = p[Key.model] ?: preset.defaultModel,
            baseUrl = p[Key.baseUrl] ?: preset.chatBaseUrl,
            webSearchEnabled = p[Key.webSearch] ?: true,
            customSearchUrl = p[Key.customSearchUrl].orEmpty(),
            temperature = p[Key.temperature]?.toFloatOrNull() ?: 0.7f,
            paletteId = p[Key.paletteId] ?: "ink_gold",
            ttsEnabled = p[Key.ttsEnabled] ?: false,
            ttsSpeed = p[Key.ttsSpeed]?.toFloatOrNull() ?: 1.0f,
            allowPublicInstitution = p[Key.allowPublicInstitution] ?: true,
            allowBusiness = p[Key.allowBusiness] ?: false,
            strictOnly = p[Key.strictOnly] ?: false,
            extraKeywords = p[Key.extraKeywords].orEmpty(),
            expandParentContext = p[Key.expandParentContext] ?: true,
            amapApiKey = p[Key.amapApiKey].orEmpty(),
            showQuickBar = p[Key.showQuickBar] ?: true,
            lightMode = p[Key.lightMode] ?: false,
            followSystem = p[Key.followSystem] ?: true,
            customAccent = p[Key.customAccent].orEmpty(),
            cornerDp = p[Key.cornerDp]?.toIntOrNull() ?: 18,
            surfaceAlpha = p[Key.surfaceAlpha]?.toIntOrNull() ?: 100,
            fontId = p[Key.fontId] ?: "system_sans",
            fontScale = p[Key.fontScale]?.toFloatOrNull() ?: 1.0f,
            importedFontPath = p[Key.importedFontPath].orEmpty(),
            onboarded = p[Key.onboarded] ?: false,
            language = p[Key.language] ?: "zh"
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /** 切换服务商时同步把模型与 baseUrl 落到该预设的默认值 */
    suspend fun selectProvider(profile: ProviderProfile) {
        context.settingsDataStore.edit { p ->
            p[Key.providerId] = profile.id
            p[Key.model] = profile.defaultModel
            p[Key.baseUrl] = profile.chatBaseUrl
        }
    }

    suspend fun setApiKey(value: String) {
        context.settingsDataStore.edit { it[Key.apiKey] = value.trim() }
    }

    suspend fun setModel(value: String) {
        context.settingsDataStore.edit { it[Key.model] = value.trim() }
    }

    suspend fun setBaseUrl(value: String) {
        context.settingsDataStore.edit { it[Key.baseUrl] = value.trim() }
    }

    suspend fun setWebSearch(enabled: Boolean) {
        context.settingsDataStore.edit { it[Key.webSearch] = enabled }
    }

    suspend fun setCustomSearchUrl(value: String) {
        context.settingsDataStore.edit { it[Key.customSearchUrl] = value.trim() }
    }

    suspend fun setTemperature(value: Float) {
        context.settingsDataStore.edit { it[Key.temperature] = value.toString() }
    }

    suspend fun setPalette(key: String) {
        context.settingsDataStore.edit { it[Key.paletteId] = key }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Key.ttsEnabled] = enabled }
    }

    suspend fun setTtsSpeed(speed: Float) {
        context.settingsDataStore.edit { it[Key.ttsSpeed] = speed.toString() }
    }

    /** 严格模式与「公司门店也算」互斥：开严格时把商业放行关掉，避免自相矛盾的配置 */
    suspend fun setStrictOnly(enabled: Boolean) {
        context.settingsDataStore.edit { p ->
            p[Key.strictOnly] = enabled
            if (enabled) p[Key.allowBusiness] = false
        }
    }

    /** 反过来，打开商业放行就退出严格模式 */
    suspend fun setAllowBusiness(enabled: Boolean) {
        context.settingsDataStore.edit { p ->
            p[Key.allowBusiness] = enabled
            if (enabled) p[Key.strictOnly] = false
        }
    }

    suspend fun setAllowPublicInstitution(enabled: Boolean) {
        context.settingsDataStore.edit { it[Key.allowPublicInstitution] = enabled }
    }

    suspend fun setExtraKeywords(value: String) {
        context.settingsDataStore.edit { it[Key.extraKeywords] = value.trim() }
    }

    suspend fun setExpandParentContext(enabled: Boolean) {
        context.settingsDataStore.edit { it[Key.expandParentContext] = enabled }
    }

    suspend fun setAmapApiKey(value: String) {
        context.settingsDataStore.edit { it[Key.amapApiKey] = value.trim() }
    }

    suspend fun setShowQuickBar(enabled: Boolean) {
        context.settingsDataStore.edit { it[Key.showQuickBar] = enabled }
    }

    suspend fun setLightMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Key.lightMode] = enabled }
    }

    suspend fun setFollowSystem(enabled: Boolean) {
        context.settingsDataStore.edit { it[Key.followSystem] = enabled }
    }

    suspend fun setCustomAccent(value: String) {
        context.settingsDataStore.edit { it[Key.customAccent] = value }
    }

    suspend fun setCornerDp(value: Int) {
        context.settingsDataStore.edit { it[Key.cornerDp] = value.toString() }
    }

    suspend fun setSurfaceAlpha(value: Int) {
        context.settingsDataStore.edit { it[Key.surfaceAlpha] = value.coerceIn(40, 100).toString() }
    }

    suspend fun setFontId(value: String) {
        context.settingsDataStore.edit { it[Key.fontId] = value }
    }

    suspend fun setFontScale(value: Float) {
        context.settingsDataStore.edit { it[Key.fontScale] = value.toString() }
    }

    suspend fun setImportedFontPath(value: String) {
        context.settingsDataStore.edit { it[Key.importedFontPath] = value }
    }

    suspend fun setOnboarded(value: Boolean) {
        context.settingsDataStore.edit { it[Key.onboarded] = value }
    }

    suspend fun setLanguage(value: String) {
        context.settingsDataStore.edit { it[Key.language] = if (value == "en") "en" else "zh" }
    }

    suspend fun getCustomPresets(): List<SpotTheme> {
        val raw = context.settingsDataStore.data.first()[Key.customPresets].orEmpty()
        return decodePresets(raw)
    }

    suspend fun saveCustomPreset(theme: SpotTheme): List<SpotTheme> {
        var result: List<SpotTheme> = emptyList()
        context.settingsDataStore.edit { p ->
            val current = decodePresets(p[Key.customPresets].orEmpty()).toMutableList()
            val idx = current.indexOfFirst { it.key == theme.key }
            if (idx >= 0) current[idx] = theme else current.add(0, theme)
            p[Key.customPresets] = encodePresets(current)
            result = current
        }
        return result
    }

    suspend fun deleteCustomPreset(key: String): List<SpotTheme> {
        var result: List<SpotTheme> = emptyList()
        context.settingsDataStore.edit { p ->
            val updated = decodePresets(p[Key.customPresets].orEmpty()).filterNot { it.key == key }
            p[Key.customPresets] = encodePresets(updated)
            result = updated
        }
        return result
    }

    private fun decodePresets(raw: String): List<SpotTheme> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val key = obj.text("key").trim()
                val title = obj.text("title").trim()
                if (key.isEmpty() || title.isEmpty()) return@mapNotNull null
                val spotsArr = obj.optJSONArray("spots") ?: JSONArray()
                val spots = (0 until spotsArr.length()).mapNotNull { j ->
                    val s = spotsArr.optJSONObject(j) ?: return@mapNotNull null
                    val sName = s.text("name").trim()
                    if (sName.isEmpty()) return@mapNotNull null
                    CuratedSpot(
                        name = sName,
                        area = s.text("area").trim(),
                        blurb = s.text("blurb").trim(),
                        searchKey = s.text("searchKey").trim().ifBlank { sName }
                    )
                }
                if (spots.isEmpty()) return@mapNotNull null
                SpotTheme(
                    key = key,
                    title = title,
                    subtitle = obj.text("subtitle").trim(),
                    spots = spots
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodePresets(list: List<SpotTheme>): String {
        val arr = JSONArray()
        list.forEach { theme ->
            val spotsArr = JSONArray()
            theme.spots.forEach { s ->
                spotsArr.put(
                    JSONObject().apply {
                        put("name", s.name)
                        put("area", s.area)
                        put("blurb", s.blurb)
                        put("searchKey", s.searchKey)
                    }
                )
            }
            arr.put(
                JSONObject().apply {
                    put("key", theme.key)
                    put("title", theme.title)
                    put("subtitle", theme.subtitle)
                    put("spots", spotsArr)
                }
            )
        }
        return arr.toString()
    }
}
