package com.shanghai.memory.ui

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.shanghai.memory.R
import com.shanghai.memory.data.AppSettings
import com.shanghai.memory.data.PromptTemplates
import com.shanghai.memory.data.SettingsStore
import com.shanghai.memory.data.TtsSpeaker
import com.shanghai.memory.data.llm.ChatClient
import com.shanghai.memory.data.llm.ModelCatalog
import com.shanghai.memory.data.llm.ModelInfo
import com.shanghai.memory.data.model.ProviderProfile
import com.shanghai.memory.data.model.Providers
import com.shanghai.memory.databinding.ActivitySettingsBinding
import com.shanghai.memory.ui.theme.AppFonts
import com.shanghai.memory.ui.theme.Appearance
import com.shanghai.memory.ui.theme.FontId
import com.shanghai.memory.ui.theme.PaletteId
import com.shanghai.memory.ui.theme.ThemeFx
import com.shanghai.memory.ui.theme.ThemeState
import com.shanghai.memory.ui.theme.ThemedActivity
import com.shanghai.memory.ui.theme.colorOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * F3 BYOK 设置：Key 只落 DataStore，不上传、不打日志。
 * 连通性测试同时测量首字延迟，直接对应「首字 ≤3s」这条非功能指标。
 * 配色、明暗、字体、圆角都是即时生效，其余字段点了保存才写盘。
 */
class SettingsActivity : ThemedActivity() {
    private var ttsPreview: TtsSpeaker? = null

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: SettingsStore
    private var provider: ProviderProfile = Providers.DEEPSEEK
    private var palette: PaletteId = PaletteId.INK_GOLD
    private var font: FontId = FontId.SYSTEM_SANS
    private var testJob: Job? = null
    private var modelsJob: Job? = null

    /** 当前显示的子页，菜单页专项返回键要先退回菜单再退出 */
    private var currentPage = PAGE_MENU

    /** 回填开关状态同样会触发监听器：这时候不该当成用户操作再写一遍盘 */
    private var suppressToggle = false

    /** 当前的落盘外观，改任一项都在它基础上推一次 */
    private var appearance: AppSettings = AppSettings()

    private val pickFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFont(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = SettingsStore(this)

        binding.btnBack.setOnClickListener {
            if (currentPage == PAGE_MENU) finish() else showPage(PAGE_MENU)
        }
        binding.txtProvider.setOnClickListener { chooseProvider() }
        binding.txtPalette.setOnClickListener { choosePalette() }
        binding.btnModels.setOnClickListener { fetchModels() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnTest.setOnClickListener { testConnection() }

        binding.txtLight.setOnClickListener { chooseLight() }
        binding.txtAccent.setOnClickListener { chooseAccent() }
        binding.txtFont.setOnClickListener { chooseFont() }

        binding.menuAppearance.setOnClickListener { showPage(PAGE_APPEARANCE) }
        binding.menuModel.setOnClickListener { showPage(PAGE_MODEL) }
        binding.menuReading.setOnClickListener { showPage(PAGE_READING) }
        binding.menuGate.setOnClickListener { showPage(PAGE_GATE) }
        binding.menuOnboarding.setOnClickListener { startActivity(OnboardingActivity.intent(this)) }

        binding.sbAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.txtAlphaValue.text = getString(R.string.alpha_value, 40 + progress)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) {
                val alpha = 40 + (bar?.progress ?: 60)
                lifecycleScope.launch {
                    store.setSurfaceAlpha(alpha)
                    refreshAppearance()
                }
            }
        })

        binding.sbCorner.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.txtCornerValue.text = getString(R.string.corner_value, progress)
                previewAppearance()
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) {
                val dp = bar?.progress ?: 18
                lifecycleScope.launch {
                    store.setCornerDp(dp)
                    refreshAppearance()
                }
            }
        })

        binding.sbFontScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = scaleOf(progress)
                binding.txtFontScaleValue.text = getString(R.string.font_scale_value, scale)
                ThemeState.apply(
                    palette = ThemeState.palette,
                    light = ThemeState.light,
                    customAccent = appearance.customAccent
                        .takeIf { it.isNotBlank() }?.let { runCatching { Color.parseColor(it) }.getOrNull() },
                    font = font,
                    fontScale = scale,
                    importedFontPath = appearance.importedFontPath,
                    cornerDp = ThemeState.cornerDp
                )
                applyAppearance(binding.root)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) {
                val scale = scaleOf(bar?.progress ?: 10)
                lifecycleScope.launch { store.setFontScale(scale) }
            }
        })

        binding.swTts.setOnCheckedChangeListener { _, checked ->
            if (suppressToggle) return@setOnCheckedChangeListener
            lifecycleScope.launch { store.setTtsEnabled(checked) }
        }

        // 地标筛选：三个开关即时生效，关掉地图页会自己刷新策略
        binding.swPublicInstitution.setOnCheckedChangeListener { _, checked ->
            if (suppressToggle) return@setOnCheckedChangeListener
            lifecycleScope.launch { store.setAllowPublicInstitution(checked) }
        }
        binding.swBusiness.setOnCheckedChangeListener { _, checked ->
            if (suppressToggle) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                store.setAllowBusiness(checked)
                // 互斥：放行商业场所就退出严格模式，界面跟着走
                if (checked) binding.swStrict.isChecked = false
            }
        }
        binding.swStrict.setOnCheckedChangeListener { _, checked ->
            if (suppressToggle) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                store.setStrictOnly(checked)
                if (checked) binding.swBusiness.isChecked = false
            }
        }
        binding.etKeywords.setOnFocusChangeListener { _, focused ->
            if (!focused) {
                lifecycleScope.launch { store.setExtraKeywords(binding.etKeywords.text.toString()) }
            }
        }

        binding.sbTtsSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                showSpeed(speedOf(progress))
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) {
                val speed = speedOf(bar?.progress ?: 10)
                lifecycleScope.launch { store.setTtsSpeed(speed) }
            }
        })

        binding.btnTtsTest.setOnClickListener { previewSpeech() }

        load()

        // 改配色会触发 recreate，这里把当前子页捞回来，否则每次调色都跳回一级菜单
        val restored = savedInstanceState?.getInt(KEY_PAGE, PAGE_MENU) ?: PAGE_MENU
        if (restored != PAGE_MENU) showPage(restored, animate = false)

    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_PAGE, currentPage)
        super.onSaveInstanceState(outState)
    }

    private fun load() {
        lifecycleScope.launch {
            applySettings(store.current())
        }
    }

    private fun applySettings(settings: AppSettings) {
        provider = settings.provider
        binding.txtProvider.text = provider.displayName
        binding.etApikey.setText(settings.apiKey)
        binding.etModel.setText(settings.model)
        binding.etBaseurl.setText(settings.baseUrl)
        // 回填会触发上面那几个监听器：不当成用户操作，否则会把同样的值再写一遍盘
        suppressToggle = true
        binding.swSearch.isChecked = settings.webSearchEnabled
        binding.swTts.isChecked = settings.ttsEnabled
        binding.swPublicInstitution.isChecked = settings.allowPublicInstitution
        binding.swBusiness.isChecked = settings.allowBusiness
        binding.swStrict.isChecked = settings.strictOnly
        suppressToggle = false
        binding.etKeywords.setText(settings.extraKeywords)
        binding.sbTtsSpeed.progress = progressOf(settings.ttsSpeed)
        showSpeed(settings.ttsSpeed)
        showEngines()
        binding.groupBaseurl.visibility = if (provider.isCustom) View.VISIBLE else View.GONE

        palette = PaletteId.of(settings.paletteId)
        binding.txtPaletteName.text = palette.label
        binding.txtPaletteSwatch.setTextColor(ContextCompat.getColor(this, palette.swatchColorRes))
        binding.txtPaletteDesc.text = palette.blurb + "\n" + getString(R.string.settings_palette_desc)

        appearance = settings
        binding.txtLightValue.text = when {
            settings.followSystem -> getString(R.string.light_follow)
            settings.lightMode -> getString(R.string.light_on)
            else -> getString(R.string.light_off)
        }

        runCatching { Color.parseColor(settings.customAccent) }
            .onSuccess { binding.txtAccentValue.setTextColor(it) }

        // 主色只在「自定义」色板下可改：预设色板自己有一套完整配色，
        // 允许单独改主色会得到预设背景 + 外来强调色的怪异组合。
        val accentEditable = palette == PaletteId.CUSTOM
        binding.txtAccent.isEnabled = accentEditable
        binding.txtAccent.alpha = if (accentEditable) 1f else 0.45f
        binding.txtAccentValue.text = if (!accentEditable) {
            getString(R.string.accent_locked)
        } else if (settings.customAccent.isBlank()) {
            getString(R.string.accent_pick)
        } else {
            settings.customAccent
        }

        binding.sbAlpha.progress = (settings.surfaceAlpha - 40).coerceIn(0, 60)
        binding.txtAlphaValue.text = getString(R.string.alpha_value, settings.surfaceAlpha)

        binding.sbCorner.progress = settings.cornerDp.coerceIn(0, 28)
        binding.txtCornerValue.text = getString(R.string.corner_value, settings.cornerDp)

        font = FontId.of(settings.fontId)
        binding.txtFontValue.text =
            if (font == FontId.IMPORTED && settings.importedFontPath.isNotBlank()) {
                "我导入的字体"
            } else {
                font.label
            }
        binding.txtFontDesc.text = font.blurb

        binding.sbFontScale.progress = progressOfScale(settings.fontScale)
        binding.txtFontScaleValue.text = getString(R.string.font_scale_value, settings.fontScale)
        previewAppearance()

        updateHint()

        // 「切到自定义后自动弹选色」：界面会先重建，所以标记等到这里再消费，
        // 这时 palette 字段刚从落盘值回填，守卫不会误拦。
        if (pendingAccentPicker) {
            pendingAccentPicker = false
            binding.root.post { chooseAccent() }
        }
    }

    /**
     * 一级菜单 → 子页的切换。
     * 只在这一处控制可见性、标题、底部动作条，避免各页各自记状态。
     *
     * [animate] 传 false 用于 recreate 后恢复现场，避免重建时再放一遍动画。
     */
    private fun showPage(page: Int, animate: Boolean = true) {
        if (page == currentPage && animate) return
        val pages = listOf(
            PAGE_MENU to binding.pageMenu,
            PAGE_APPEARANCE to binding.pageAppearance,
            PAGE_MODEL to binding.pageModel,
            PAGE_READING to binding.pageReading,
            PAGE_GATE to binding.pageGate
        )
        val incoming = pages.firstOrNull { it.first == page }?.second ?: return
        val outgoing = pages.firstOrNull { it.first == currentPage }?.second
        val forward = page > currentPage
        currentPage = page

        // 菜单页不需要「测试连通性/保存」；测试按钮只在模型页有意义
        binding.footerActions.visibility = if (page == PAGE_MENU) View.GONE else View.VISIBLE
        binding.btnTest.visibility = if (page == PAGE_MODEL) View.VISIBLE else View.GONE
        binding.txtSettingsTitle.setText(
            when (page) {
                PAGE_APPEARANCE -> R.string.settings_menu_appearance
                PAGE_MODEL -> R.string.settings_menu_model
                PAGE_READING -> R.string.settings_menu_reading
                PAGE_GATE -> R.string.settings_menu_gate
                else -> R.string.settings_title
            }
        )

        if (!animate) {
            pages.forEach { (key, view) -> view.visibility = if (key == page) View.VISIBLE else View.GONE }
            return
        }
        if (outgoing != null && outgoing !== incoming) {
            ThemeFx.slideOutPage(outgoing, forward)
        }
        ThemeFx.slideInPage(incoming, forward)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (currentPage != PAGE_MENU) showPage(PAGE_MENU) else super.onBackPressed()
    }

    private fun updateHint() {
        val base = binding.etBaseurl.text.toString().trim()
        val insecure = base.startsWith("http://") && !isLoopback(base)
        val text = when {
            insecure -> "这个端点用的是 http://，Key 与请求内容会明文经过网络。能换 https 就换，只有本机或可信局域网才建议保留。"
            provider.isCustom -> "自定义端点需为 OpenAI 兼容的 /v1 前缀，程序会自动拼接 /chat/completions。"
            provider.hasWebSearch -> "该服务商支持联网检索：会先取公开资料，再交给模型讲述，并在结尾标注来源性质。"
            else -> "该服务商无官方检索接口，将以纯对话方式生成，结尾标注「未联网核实」。"
        }
        binding.txtStatus.text = text
    }

    private fun isLoopback(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        return host == "127.0.0.1" || host == "localhost" || host == "::1" || host.startsWith("10.") ||
            host.startsWith("192.168.") || host.startsWith("172.16.") || host.startsWith("172.17.")
    }

    private fun choosePalette() {
        val items = PaletteId.entries.map { "${it.label} —— ${it.blurb}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_section_appearance)
            .setSingleChoiceItems(items, PaletteId.entries.indexOf(palette)) { dialog, which ->
                dialog.dismiss()
                val picked = PaletteId.entries[which]
                palette = picked
                lifecycleScope.launch {
                    store.setPalette(picked.key)
                    if (picked != PaletteId.CUSTOM) {
                        // 回到预设时把之前挑过的自定义主色清掉，否则下次再切回自定义会带着旧颜色
                        store.setCustomAccent("")
                    }
                    if (picked == PaletteId.CUSTOM) pendingAccentPicker = true
                    refreshAppearance()
                    // 没有触发重建（本来就在自定义色板上）时，等一会儿自己把选色弹出来；
                    // 触发了重建的话这个 post 会随旧视图一起丢，改由 onCreate 接手。
                    binding.root.postDelayed({
                        if (pendingAccentPicker) {
                            pendingAccentPicker = false
                            chooseAccent()
                        }
                    }, 400L)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun chooseLight() {
        val labels = arrayOf(
            getString(R.string.light_follow),
            getString(R.string.light_on),
            getString(R.string.light_off)
        )
        val checked = when {
            appearance.followSystem -> 0
            appearance.lightMode -> 1
            else -> 2
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_light)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                lifecycleScope.launch {
                    when (which) {
                        0 -> store.setFollowSystem(true)
                        1 -> {
                            store.setFollowSystem(false)
                            store.setLightMode(true)
                        }
                        else -> {
                            store.setFollowSystem(false)
                            store.setLightMode(false)
                        }
                    }
                    refreshAppearance()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun chooseAccent() {
        if (palette != PaletteId.CUSTOM) {
            Toast.makeText(this, R.string.accent_need_custom, Toast.LENGTH_SHORT).show()
            return
        }
        val presets = listOf(
            "赤陶" to "#E06A4F", "琥珀" to "#E0A23A", "苔绿" to "#7FA05B",
            "松青" to "#4FA39B", "靛蓝" to "#5B7FD1", "藕紫" to "#9A6FBF",
            "樱粉" to "#F0A2B8", "赭石" to "#B5763F", "石墨" to "#8A9099",
            "湖蓝" to "#4E8FB8", "青瓷" to "#8FBFA8", "绛紫" to "#8E5A7A"
        )
        val items = (
            listOf(getString(R.string.accent_preset)) +
                presets.map { "${it.first}    ${it.second}" } +
                getString(R.string.accent_custom)
            ).toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_accent)
            .setItems(items) { _, which ->
                when {
                    which == 0 -> lifecycleScope.launch {
                        store.setCustomAccent("")
                        refreshAppearance()
                    }

                    which == items.size - 1 -> askHexColor()

                    else -> {
                        val hex = presets[which - 1].second
                        lifecycleScope.launch {
                            store.setCustomAccent(hex)
                            refreshAppearance()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun askHexColor() {
        val input = EditText(this).apply {
            hint = "#F0A2B8"
            setText(appearance.customAccent.trimStart('#'))
            setTextColor(colorOf(R.attr.shText))
            setHintTextColor(colorOf(R.attr.shTextMuted))
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_accent)
            .setView(box)
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text.toString().trim().trimStart('#')
                val color = if (raw.length == 6 || raw.length == 8) {
                    runCatching { Color.parseColor("#$raw") }.getOrNull()
                } else {
                    null
                }
                if (color == null) {
                    input.error = "要 6 位十六进制，例如 F0A2B8"
                    return@setOnClickListener
                }
                dialog.dismiss()
                lifecycleScope.launch {
                    store.setCustomAccent(Appearance.toHex(color))
                    refreshAppearance()
                }
            }
        }
        dialog.show()
    }

    private fun chooseFont() {
        val entries = FontId.entries.toList()
        val labels = (entries.map { "${it.label} —— ${it.blurb}" } + getString(R.string.font_import))
            .toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.font_pick_title)
            .setSingleChoiceItems(labels, entries.indexOf(font)) { dialog, which ->
                dialog.dismiss()
                if (which == labels.size - 1) {
                    pickFont.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*"))
                } else {
                    val picked = entries[which]
                    if (picked == FontId.IMPORTED && appearance.importedFontPath.isBlank()) {
                        Toast.makeText(this, R.string.font_import_fail, Toast.LENGTH_SHORT).show()
                    } else {
                        lifecycleScope.launch {
                            store.setFontId(picked.key)
                            refreshAppearance()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 把选中的字体文件复制到应用私有目录；系统授权只是临时读权限，不能直接引用 */
    private fun importFont(uri: Uri) {
        lifecycleScope.launch {
            val dir = File(filesDir, "fonts").apply { mkdirs() }
            val dest = File(dir, "imported.ttf")
            val copied = runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    // 上限 20MB：字体本身就几百 KB 到几 MB，超了多半是选错文件，
                    // 不设上限等于让一次误操作把私有目录撑爆。
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    dest.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_FONT_BYTES) error("字体文件超过 20MB")
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: error("无法读取所选文件")
                dest
            }.onFailure {
                // 中途失败会留下写了一半的 imported.ttf，下次启动会被当成有效字体加载
                runCatching { dest.delete() }
            }
            copied.onSuccess { file ->
                AppFonts.of(this@SettingsActivity).clear()
                store.setImportedFontPath(file.absolutePath)
                store.setFontId(FontId.IMPORTED.key)
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.font_import_ok, file.name),
                    Toast.LENGTH_SHORT
                ).show()
                refreshAppearance()
            }.onFailure {
                Toast.makeText(this@SettingsActivity, R.string.font_import_fail, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 外观类改动统一走这里：落盘 → 更新进程内状态 → 重建界面 */
    private suspend fun refreshAppearance() {
        Appearance.applyFrom(store.current(), Appearance.systemDark(this))
        recreate()
    }

    private fun previewAppearance() {
        binding.txtPreview.setTextSize(
            android.util.TypedValue.COMPLEX_UNIT_SP, 15f * ThemeState.fontScale
        )
    }

    private fun scaleOf(progress: Int): Float = 0.85f + progress * 0.05f

    private fun progressOfScale(scale: Float): Int =
        ((scale - 0.85f) / 0.05f).roundToInt().coerceIn(0, 12)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun chooseProvider() {
        val names = Providers.presets.map { it.displayName }.toTypedArray()
        val checked = Providers.presets.indexOfFirst { it.id == provider.id }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_provider)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                dialog.dismiss()
                val picked = Providers.presets[which]
                lifecycleScope.launch {
                    store.selectProvider(picked)
                    applySettings(store.current())
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 用当前界面上的 Key 与 Base URL 拉一次 /models，直接填进模型框。 */
    private fun fetchModels() {
        val draft = currentInput()
        if (draft.apiKey.isBlank()) {
            binding.txtStatus.text = "先填 API Key，才能拉取模型列表。"
            return
        }

        modelsJob?.cancel()
        binding.btnModels.isEnabled = false
        binding.txtStatus.text = "正在请求 ${draft.modelsEndpoint} …"

        modelsJob = lifecycleScope.launch {
            try {
                val list = ModelCatalog.list(draft)
                binding.txtStatus.text = "拉取到 ${list.size} 个模型，选一个吧。"
                showModels(list)
            } catch (t: Throwable) {
                binding.txtStatus.text = "获取失败：" + (t.message ?: "未知错误")
            } finally {
                binding.btnModels.isEnabled = true
            }
        }
    }

    private fun showModels(list: List<ModelInfo>) {
        val labels = list.map { model ->
            buildString {
                append(model.displayName)
                append("  ·  ")
                append(model.id)
                if (model.subtitle.isNotBlank()) {
                    append("\n")
                    append(model.subtitle)
                }
            }
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("可用模型（${list.size}）")
            .setItems(labels) { _, which ->
                val picked = list[which]
                binding.etModel.setText(picked.id)
                binding.txtStatus.text = "已填入 ${picked.id}，记得点保存。"
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun currentInput(): AppSettings {
        val base = binding.etBaseurl.text.toString().trim()
        return AppSettings(
            providerId = provider.id,
            apiKey = binding.etApikey.text.toString().trim(),
            model = binding.etModel.text.toString().trim(),
            baseUrl = base.ifBlank { provider.chatBaseUrl },
            webSearchEnabled = binding.swSearch.isChecked,
            paletteId = palette.key,
            ttsEnabled = binding.swTts.isChecked,
            ttsSpeed = speedOf(binding.sbTtsSpeed.progress),
            allowPublicInstitution = binding.swPublicInstitution.isChecked,
            allowBusiness = binding.swBusiness.isChecked,
            strictOnly = binding.swStrict.isChecked,
            extraKeywords = binding.etKeywords.text.toString().trim(),
            lightMode = appearance.lightMode,
            followSystem = appearance.followSystem,
            customAccent = appearance.customAccent,
            cornerDp = binding.sbCorner.progress,
            fontId = font.key,
            fontScale = scaleOf(binding.sbFontScale.progress),
            importedFontPath = appearance.importedFontPath,
            // 下面几项界面上没有对应控件，沿用当前快照。
            // 漏掉它们的话，任何拿 draft 整体覆盖的调用都会把检索端点、温度、
            // 背景透明度、引导完成标记悄悄重置成默认值
            customSearchUrl = appearance.customSearchUrl,
            temperature = appearance.temperature,
            surfaceAlpha = appearance.surfaceAlpha,
            onboarded = appearance.onboarded
        )
    }

    private fun save() {
        val draft = currentInput()
        // Key 只决定聊天与检索能不能用，不该拦住外观、朗读、地标筛选这些页面的保存：
        // 早先这里无条件 return，用户在别的子页点保存会静默失败，还以为已经存上了
        if (draft.apiKey.isBlank() && currentPage == PAGE_MODEL) {
            binding.txtStatus.text = "先填 API Key，再保存。"
            return
        }
        lifecycleScope.launch {
            store.setApiKey(draft.apiKey)
            store.setModel(draft.model)
            store.setBaseUrl(draft.baseUrl)
            store.setWebSearch(draft.webSearchEnabled)
            store.setTtsEnabled(draft.ttsEnabled)
            store.setTtsSpeed(draft.ttsSpeed)
            // 关键词框可能一直没失焦，保存时再兜一次
            store.setExtraKeywords(draft.extraKeywords)
            binding.txtStatus.text = if (draft.apiKey.isBlank()) {
                "已保存。还没填 API Key，聊天与检索暂时不可用。"
            } else {
                "已保存到本机。Key 仅存于本应用的 DataStore。"
            }
            Toast.makeText(this@SettingsActivity, "已保存", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testConnection() {
        val draft = currentInput()
        if (draft.apiKey.isBlank() || draft.model.isBlank()) {
            binding.txtStatus.text = "Key 与模型名都要填。"
            return
        }

        testJob?.cancel()
        binding.btnTest.isEnabled = false
        binding.txtStatus.text = "正在请求 ${draft.chatEndpoint} …"

        testJob = lifecycleScope.launch {
            val start = SystemClock.elapsedRealtime()
            var firstCharMs = -1L
            val reply = StringBuilder()
            try {
                ChatClient().stream(
                    settings = draft,
                    systemPrompt = "你是连通性测试助手，只按要求回复，不作解释。",
                    userPrompt = PromptTemplates.pingUserPrompt(),
                    maxTokens = 32
                ).collect { chunk ->
                    if (firstCharMs < 0) firstCharMs = SystemClock.elapsedRealtime() - start
                    reply.append(chunk)
                }
                val total = SystemClock.elapsedRealtime() - start
                binding.txtStatus.text = buildString {
                    append("连通正常。\n")
                    append("首字 ").append(if (firstCharMs < 0) "—" else "${firstCharMs} ms")
                    append(" · 总计 ").append(total).append(" ms\n")
                    append("回复：").append(reply.toString().trim().take(40))
                }
            } catch (t: Throwable) {
                binding.txtStatus.text = "连接失败：" + (t.message ?: "未知错误")
            } finally {
                binding.btnTest.isEnabled = true
            }
        }
    }

    private fun speedOf(progress: Int): Float = 0.5f + progress * 0.05f

    private fun progressOf(speed: Float): Int = ((speed - 0.5f) / 0.05f).roundToInt().coerceIn(0, 20)

    private fun showSpeed(speed: Float) {
        binding.txtTtsSpeed.text = "语速 %.1f×".format(speed)
    }

    /**
     * 试听：这是整条朗读链路上最容易出问题的一环，
     * 给用户一个能立刻验证「到底是有声音还是没声音」的按钮，
     * 并把引擎状态摊开写清楚。
     */
    private fun previewSpeech() {
        val speaker = ttsPreview ?: TtsSpeaker(applicationContext).also { ttsPreview = it }
        speaker.onAvailabilityChanged = { _, detail ->
            runOnUiThread {
                binding.txtTtsState.text = detail
                val broken = speaker.state != TtsSpeaker.State.READY && speaker.state != TtsSpeaker.State.INIT
                if (broken) Toast.makeText(this, detail, Toast.LENGTH_LONG).show()
            }
        }
        binding.txtTtsState.text = speaker.detail
        speaker.onStateChanged = { speaking ->
            binding.btnTtsTest.text =
                if (speaking) "正在朗读…" else getString(R.string.settings_tts_test)
        }
        speaker.speak(getString(R.string.settings_preview_text), speedOf(binding.sbTtsSpeed.progress))
    }

    /** 把系统里装了哪些语音引擎写进朗读栏，出问题时至少知道现状 */
    private fun showEngines() {
        val engines = TtsSpeaker.installedEngines(this)
        binding.txtTtsState.text = if (engines.isEmpty()) {
            getString(R.string.settings_tts_none)
        } else {
            getString(R.string.settings_tts_engines, engines.joinToString("、"))
        }
    }

    override fun onDestroy() {
        testJob?.cancel()
        modelsJob?.cancel()
        ttsPreview?.shutdown()
        ttsPreview = null
        super.onDestroy()
    }

    private companion object {
        const val MAX_FONT_BYTES = 20L * 1024 * 1024

        const val PAGE_MENU = 0
        const val PAGE_APPEARANCE = 1
        const val PAGE_MODEL = 2
        const val PAGE_READING = 3
        const val PAGE_GATE = 4
        const val KEY_PAGE = "settings_page"
        /** 切到「自定义」后要弹主色选择，但界面会先 recreate，只能挂个标记等重建完再弹 */
        @Volatile var pendingAccentPicker = false
    }
}
