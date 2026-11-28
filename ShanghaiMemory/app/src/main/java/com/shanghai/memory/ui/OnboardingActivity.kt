package com.shanghai.memory.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.shanghai.memory.R
import com.shanghai.memory.data.PromptTemplates
import com.shanghai.memory.data.SettingsStore
import com.shanghai.memory.data.llm.ChatClient
import com.shanghai.memory.data.model.ProviderProfile
import com.shanghai.memory.data.model.Providers
import com.shanghai.memory.databinding.ActivityOnboardingBinding
import com.shanghai.memory.ui.theme.Appearance
import com.shanghai.memory.ui.theme.PaletteColors
import com.shanghai.memory.ui.theme.PaletteId
import com.shanghai.memory.ui.theme.ThemeFx
import com.shanghai.memory.ui.theme.ThemeState
import com.shanghai.memory.ui.theme.ThemedActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 新用户引导（1.2.0 视觉交互重做版）：
 * 图示化总览 -> 交互选服务商/测 Key -> 实时换装卡片预览 -> 交互微缩地图找故事 -> 路线流程图与离线著名地标预览。
 */
class OnboardingActivity : ThemedActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val store by lazy { SettingsStore(this) }
    private var page = 1
    private var paletteKey: String = PaletteId.INK_GOLD.key
    private var selectedProviderId: String = Providers.DEEPSEEK.id
    private var activePin = PIN_HERITAGE
    private var navDemoOn = false
    private var p5TabRoute = true
    private var p5Stop = 1
    private var testJob: Job? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (page > 1) applyPage(page - 1, animate = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backCallback)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        page = savedInstanceState?.getInt(KEY_PAGE) ?: 1
        activePin = savedInstanceState?.getInt(KEY_PIN) ?: PIN_HERITAGE
        navDemoOn = savedInstanceState?.getBoolean(KEY_NAV_DEMO) ?: false
        p5TabRoute = savedInstanceState?.getBoolean(KEY_P5_TAB) ?: true
        p5Stop = savedInstanceState?.getInt(KEY_P5_STOP) ?: 1

        setUpPagers()
        setUpKey()
        setUpAppearance()
        setUpMapDemo()
        setUpRouteDemo()
        applyPage(page, animate = false)
        loadCurrent()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGE, page)
        outState.putInt(KEY_PIN, activePin)
        outState.putBoolean(KEY_NAV_DEMO, navDemoOn)
        outState.putBoolean(KEY_P5_TAB, p5TabRoute)
        outState.putInt(KEY_P5_STOP, p5Stop)
    }

    private fun pages(): List<View> = listOf(
        binding.page1, binding.page2, binding.page3, binding.page4, binding.page5
    )

    private fun dots(): List<View> = listOf(
        binding.dot1, binding.dot2, binding.dot3, binding.dot4, binding.dot5
    )

    private fun setUpPagers() {
        binding.btnPrev.setOnClickListener {
            if (page > 1) applyPage(page - 1, animate = true)
        }
        binding.btnNext.setOnClickListener {
            if (page >= pages().size) finishTutorial() else applyPage(page + 1, animate = true)
        }
        binding.btnSkip.setOnClickListener { finishTutorial() }
    }

    private fun applyPage(target: Int, animate: Boolean) {
        val old = page
        val moving = animate && target != old
        if (moving) {
            ThemeFx.slideOutPage(pages()[old - 1], forward = target > old)
        }
        pages().forEachIndexed { i, v ->
            when {
                i == target - 1 -> v.visibility = View.VISIBLE
                moving && i == old - 1 -> Unit
                else -> v.visibility = View.GONE
            }
        }
        page = target
        backCallback.isEnabled = target > 1
        if (animate) ThemeFx.slideInPage(pages()[target - 1], forward = target > old)

        binding.txtOnbStep.text = "$target / ${pages().size}"
        binding.btnPrev.visibility = if (target > 1) View.VISIBLE else View.GONE
        binding.btnNext.setText(if (target == pages().size) R.string.onb_start else R.string.onb_next)

        val colors = PaletteColors.of(PaletteId.of(paletteKey), ThemeState.light)
        dots().forEachIndexed { i, dot ->
            val on = i == target - 1
            dot.alpha = if (on) 1f else 0.32f
            dot.backgroundTintList = ColorStateList.valueOf(if (on) colors.accent else colors.textMuted)
        }

        if (target == 4) {
            binding.onbMapStage.post { moveCrosshairTo(activePin, animate = false) }
        }
    }

    // ---- 第 2 页：服务商与 Key ----

    private fun setUpKey() {
        binding.provDeepseek.setOnClickListener { pickProvider(Providers.DEEPSEEK) }
        binding.provZhipu.setOnClickListener { pickProvider(Providers.ZHIPU) }
        binding.provKimi.setOnClickListener { pickProvider(Providers.KIMI) }
        binding.provCustom.setOnClickListener { pickProvider(Providers.CUSTOM) }
        binding.etKey.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveKeyIfAny()
        }
        binding.btnOnbTestKey.setOnClickListener { testKeyConnection() }
    }

    private fun pickProvider(profile: ProviderProfile) {
        selectedProviderId = profile.id
        lifecycleScope.launch {
            store.selectProvider(profile)
            refreshProviderUi(profile)
        }
    }

    private fun refreshProviderUi(profile: ProviderProfile) {
        val colors = PaletteColors.of(PaletteId.of(paletteKey), ThemeState.light)
        val chips = listOf(
            Providers.DEEPSEEK.id to binding.provDeepseek,
            Providers.ZHIPU.id to binding.provZhipu,
            Providers.KIMI.id to binding.provKimi,
            Providers.CUSTOM.id to binding.provCustom
        )
        chips.forEach { (id, v) ->
            val on = id == profile.id
            v.setTextColor(if (on) colors.onAccent else colors.text)
            v.backgroundTintList = if (on) ColorStateList.valueOf(colors.accent) else null
        }
        binding.txtProvCapability.setText(
            when (profile.id) {
                Providers.DEEPSEEK.id -> R.string.onb_p2_cap_deepseek
                Providers.ZHIPU.id -> R.string.onb_p2_cap_zhipu
                Providers.KIMI.id -> R.string.onb_p2_cap_kimi
                else -> R.string.onb_p2_cap_custom
            }
        )
    }

    private fun saveKeyIfAny() {
        val value = binding.etKey.text?.toString()?.trim().orEmpty()
        if (value.isEmpty()) return
        lifecycleScope.launch {
            store.setApiKey(value)
            val colors = PaletteColors.of(PaletteId.of(paletteKey), ThemeState.light)
            binding.txtKeyState.setTextColor(colors.ok)
            binding.txtKeyState.visibility = View.VISIBLE
            binding.txtKeyState.text = getString(R.string.onb_p2_saved)
        }
    }

    private fun testKeyConnection() {
        val en = ThemeState.isEnglish
        val colors = PaletteColors.of(PaletteId.of(paletteKey), ThemeState.light)
        val entered = binding.etKey.text?.toString()?.trim().orEmpty()
        if (entered.isEmpty()) {
            binding.txtKeyState.setTextColor(colors.textMuted)
            binding.txtKeyState.visibility = View.VISIBLE
            binding.txtKeyState.text = getString(R.string.onb_p2_need_key_test)
            return
        }
        testJob?.cancel()
        binding.btnOnbTestKey.isEnabled = false
        binding.txtKeyState.setTextColor(colors.textMuted)
        binding.txtKeyState.visibility = View.VISIBLE
        binding.txtKeyState.text = getString(R.string.onb_p2_testing)

        testJob = lifecycleScope.launch {
            store.setApiKey(entered)
            val draft = store.current().copy(apiKey = entered)
            val start = SystemClock.elapsedRealtime()
            var firstMs = -1L
            try {
                ChatClient().stream(
                    settings = draft,
                    systemPrompt = if (en) "Reply briefly as requested." else "只按要求简短回复。",
                    userPrompt = PromptTemplates.pingUserPrompt(),
                    maxTokens = 24
                ).collect {
                    if (firstMs < 0) firstMs = SystemClock.elapsedRealtime() - start
                }
                binding.txtKeyState.setTextColor(colors.ok)
                binding.txtKeyState.text = if (en) {
                    "Connected & saved (${if (firstMs < 0) "—" else "$firstMs ms"} first token)."
                } else {
                    "连接成功并已保存（首字响应 ${if (firstMs < 0) "—" else "$firstMs ms"}）。"
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                val msg = t.message ?: if (en) "Connection failed" else "连接失败"
                binding.txtKeyState.setTextColor(colors.danger)
                binding.txtKeyState.text = if (en) "Saved locally, test failed: $msg" else "已先保存 Key，测试未通过：$msg"
            } finally {
                binding.btnOnbTestKey.isEnabled = true
            }
        }
    }

    private fun loadCurrent() {
        lifecycleScope.launch {
            val settings = store.current()
            paletteKey = settings.paletteId
            selectedProviderId = settings.providerId
            binding.etKey.setText(settings.apiKey)
            if (settings.apiKey.isNotBlank()) {
                binding.txtKeyState.visibility = View.VISIBLE
                binding.txtKeyState.text = getString(R.string.onb_p2_saved)
            }
            refreshProviderUi(settings.provider)
            refreshAppearanceUi(settings.paletteId, settings.followSystem, settings.lightMode)
            refreshMapDemoUi()
            refreshRouteDemoUi()
            applyPage(page, animate = false)
        }
    }

    // ---- 第 3 页：外观 ----

    private fun setUpAppearance() {
        binding.pickInkGold.setOnClickListener { pickPalette(PaletteId.INK_GOLD.key) }
        binding.pickSakura.setOnClickListener { pickPalette(PaletteId.SAKURA.key) }
        binding.pickCeladon.setOnClickListener { pickPalette(PaletteId.CELADON.key) }
        binding.lightFollow.setOnClickListener { pickLight(follow = true, light = false) }
        binding.lightOn.setOnClickListener { pickLight(follow = false, light = true) }
        binding.lightOff.setOnClickListener { pickLight(follow = false, light = false) }
    }

    private fun pickPalette(key: String) {
        lifecycleScope.launch {
            store.setPalette(key)
            store.setCustomAccent("")
            paletteKey = key
            val settings = store.current()
            Appearance.applyFrom(settings, isSystemDark())
            refreshProviderUi(settings.provider)
            refreshAppearanceUi(settings.paletteId, settings.followSystem, settings.lightMode)
            refreshMapDemoUi()
            refreshRouteDemoUi()
            applyAppearance(binding.root)
            applyPage(page, animate = false)
        }
    }

    private fun pickLight(follow: Boolean, light: Boolean) {
        lifecycleScope.launch {
            store.setFollowSystem(follow)
            if (!follow) store.setLightMode(light)
            val settings = store.current()
            Appearance.applyFrom(settings, isSystemDark())
            refreshAppearanceUi(settings.paletteId, settings.followSystem, settings.lightMode)
            binding.root.post { recreate() }
        }
    }

    private fun refreshAppearanceUi(paletteKeyValue: String, follow: Boolean, light: Boolean) {
        val colors = PaletteColors.of(PaletteId.of(paletteKeyValue), ThemeState.light)
        val picks = mapOf(
            PaletteId.INK_GOLD.key to binding.pickInkGold,
            PaletteId.SAKURA.key to binding.pickSakura,
            PaletteId.CELADON.key to binding.pickCeladon,
        )
        picks.forEach { (key, view) ->
            val on = key == paletteKeyValue
            view.alpha = if (on) 1f else 0.55f
            view.backgroundTintList = if (on) {
                ColorStateList.valueOf((colors.accent and 0x00FFFFFF) or (0x30 shl 24))
            } else {
                null
            }
        }
        val chosen = when {
            follow -> binding.lightFollow
            light -> binding.lightOn
            else -> binding.lightOff
        }
        listOf(binding.lightFollow, binding.lightOn, binding.lightOff).forEach { v ->
            val on = v === chosen
            v.setTextColor(if (on) colors.onAccent else colors.textMuted)
            v.backgroundTintList = if (on) ColorStateList.valueOf(colors.accent) else null
        }
        binding.txtOnbPreview.setTextColor(colors.text)
        binding.txtOnbPreviewTag.setTextColor(colors.accent)
        binding.btnOnbPreviewCta.setTextColor(colors.onAccent)
        binding.btnOnbPreviewCta.backgroundTintList = ColorStateList.valueOf(colors.accent)
    }

    // ---- 第 4 页：交互微缩地图演示 ----

    private fun setUpMapDemo() {
        binding.pinHeritage.setOnClickListener { selectMapPin(PIN_HERITAGE) }
        binding.pinSubspot.setOnClickListener { selectMapPin(PIN_SUBSPOT) }
        binding.pinShop.setOnClickListener { selectMapPin(PIN_SHOP) }
        binding.btnOnbNavDemo.setOnClickListener {
            navDemoOn = !navDemoOn
            if (navDemoOn) {
                binding.imgOnbCompass.animate().rotationBy(45f).setDuration(260).start()
            } else {
                binding.imgOnbCompass.animate().rotation(0f).setDuration(220).start()
            }
            refreshMapDemoUi()
        }
    }

    private fun selectMapPin(pinId: Int) {
        activePin = pinId
        refreshMapDemoUi()
        moveCrosshairTo(pinId, animate = true)
    }

    private fun moveCrosshairTo(pinId: Int, animate: Boolean) {
        val targetView = when (pinId) {
            PIN_SUBSPOT -> binding.pinSubspot
            PIN_SHOP -> binding.pinShop
            else -> binding.pinHeritage
        }
        val stage = binding.onbMapStage
        if (stage.width == 0 || targetView.width == 0) return
        val dx = (targetView.x + targetView.width / 2f) - (stage.width / 2f)
        val dy = (targetView.y + targetView.height / 2f) - (stage.height / 2f)
        val crosshair = binding.onbCrosshair
        if (animate) {
            crosshair.animate().translationX(dx).translationY(dy).setDuration(240).start()
        } else {
            crosshair.translationX = dx
            crosshair.translationY = dy
        }
    }

    private fun refreshMapDemoUi() {
        val colors = PaletteColors.of(PaletteId.of(paletteKey), ThemeState.light)
        val pins = listOf(
            PIN_HERITAGE to binding.pinHeritage,
            PIN_SUBSPOT to binding.pinSubspot,
            PIN_SHOP to binding.pinShop
        )
        pins.forEach { (id, v) ->
            val on = id == activePin
            v.setTextColor(if (on) colors.onAccent else if (id == PIN_SHOP) colors.textMuted else colors.text)
            v.backgroundTintList = if (on) ColorStateList.valueOf(colors.accent) else null
        }

        when (activePin) {
            PIN_HERITAGE -> {
                binding.txtSimTitle.setText(R.string.onb_p4_sim_heritage_title)
                binding.txtSimBadge.setText(R.string.onb_p4_sim_heritage_badge)
                binding.txtSimBadge.setTextColor(colors.accent)
                binding.txtSimDesc.setText(R.string.onb_p4_sim_heritage_desc)
                binding.rowStoryParts.visibility = View.VISIBLE
            }
            PIN_SUBSPOT -> {
                binding.txtSimTitle.setText(R.string.onb_p4_sim_subspot_title)
                binding.txtSimBadge.setText(R.string.onb_p4_sim_subspot_badge)
                binding.txtSimBadge.setTextColor(colors.accent)
                binding.txtSimDesc.setText(R.string.onb_p4_sim_subspot_desc)
                binding.rowStoryParts.visibility = View.VISIBLE
            }
            else -> {
                binding.txtSimTitle.setText(R.string.onb_p4_sim_shop_title)
                binding.txtSimBadge.setText(R.string.onb_p4_sim_shop_badge)
                binding.txtSimBadge.setTextColor(colors.textMuted)
                binding.txtSimDesc.setText(R.string.onb_p4_sim_shop_desc)
                binding.rowStoryParts.visibility = View.GONE
            }
        }

        binding.txtOnbNavDemo.setText(if (navDemoOn) R.string.onb_p4_nav_on else R.string.onb_p4_nav_off)
        binding.txtOnbNavDemo.setTextColor(if (navDemoOn) colors.accent else colors.textMuted)
        binding.imgOnbCompass.imageTintList = ColorStateList.valueOf(if (navDemoOn) colors.accent else colors.textMuted)
    }

    // ---- 第 5 页：路线图解与离线著名地标 ----

    private fun setUpRouteDemo() {
        binding.tabOnbRoute.setOnClickListener {
            p5TabRoute = true
            refreshRouteDemoUi()
        }
        binding.tabOnbFamous.setOnClickListener {
            p5TabRoute = false
            refreshRouteDemoUi()
        }
        binding.demoStop1.setOnClickListener { p5Stop = 1; refreshRouteDemoUi() }
        binding.demoStop2.setOnClickListener { p5Stop = 2; refreshRouteDemoUi() }
        binding.demoStop3.setOnClickListener { p5Stop = 3; refreshRouteDemoUi() }
    }

    private fun refreshRouteDemoUi() {
        val colors = PaletteColors.of(PaletteId.of(paletteKey), ThemeState.light)
        binding.panelOnbRoute.visibility = if (p5TabRoute) View.VISIBLE else View.GONE
        binding.panelOnbFamous.visibility = if (p5TabRoute) View.GONE else View.VISIBLE

        binding.tabOnbRoute.setTextColor(if (p5TabRoute) colors.onAccent else colors.textMuted)
        binding.tabOnbRoute.backgroundTintList = if (p5TabRoute) ColorStateList.valueOf(colors.accent) else null
        binding.tabOnbFamous.setTextColor(if (!p5TabRoute) colors.onAccent else colors.textMuted)
        binding.tabOnbFamous.backgroundTintList = if (!p5TabRoute) ColorStateList.valueOf(colors.accent) else null

        listOf(
            1 to binding.demoStop1,
            2 to binding.demoStop2,
            3 to binding.demoStop3
        ).forEach { (idx, v) ->
            val on = idx == p5Stop
            v.setTextColor(if (on) colors.onAccent else colors.text)
            v.backgroundTintList = if (on) ColorStateList.valueOf(colors.accent) else null
        }

        binding.txtDemoStopDetail.setText(
            when (p5Stop) {
                2 -> R.string.onb_p5_s2_detail
                3 -> R.string.onb_p5_s3_detail
                else -> R.string.onb_p5_s1_detail
            }
        )
    }

    private fun isSystemDark(): Boolean {
        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    // ---- 收尾 ----

    private fun finishTutorial() {
        val key = binding.etKey.text?.toString()?.trim().orEmpty()
        lifecycleScope.launch {
            if (key.isNotEmpty()) store.setApiKey(key)
            store.setOnboarded(true)
            finish()
        }
    }

    override fun onDestroy() {
        testJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val KEY_PAGE = "onboarding_page"
        private const val KEY_PIN = "onboarding_pin"
        private const val KEY_NAV_DEMO = "onboarding_nav_demo"
        private const val KEY_P5_TAB = "onboarding_p5_tab"
        private const val KEY_P5_STOP = "onboarding_p5_stop"

        private const val PIN_HERITAGE = 0
        private const val PIN_SUBSPOT = 1
        private const val PIN_SHOP = 2

        fun intent(context: Context): Intent = Intent(context, OnboardingActivity::class.java)
    }
}
