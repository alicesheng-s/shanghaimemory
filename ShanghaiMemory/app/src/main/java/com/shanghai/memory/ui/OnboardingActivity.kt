package com.shanghai.memory.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.shanghai.memory.R
import com.shanghai.memory.data.SettingsStore
import com.shanghai.memory.databinding.ActivityOnboardingBinding
import com.shanghai.memory.ui.theme.Appearance
import com.shanghai.memory.ui.theme.PaletteColors
import com.shanghai.memory.ui.theme.PaletteId
import com.shanghai.memory.ui.theme.ThemeFx
import com.shanghai.memory.ui.theme.ThemeState
import com.shanghai.memory.ui.theme.ThemedActivity
import kotlinx.coroutines.launch

/**
 * 新用户引导：五页，说明用途 -> 填 Key -> 选外观 -> 教地图 -> 教路线。
 * 首次启动由 MapActivity 拉起；之后可在设置的一级菜单里再次打开。
 */
class OnboardingActivity : ThemedActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val store by lazy { SettingsStore(this) }
    private var page = 1
    private var paletteKey: String = PaletteId.INK_GOLD.key

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        page = savedInstanceState?.getInt(KEY_PAGE) ?: 1
        setUpPagers()
        setUpKey()
        setUpAppearance()
        applyPage(page, animate = false)
        loadCurrent()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGE, page)
    }

    private fun pages(): List<View> = listOf(
        binding.page1, binding.page2, binding.page3, binding.page4, binding.page5
    )

    private fun dots(): List<View> = listOf(
        binding.dot1, binding.dot2, binding.dot3, binding.dot4, binding.dot5
    )

    private fun setUpPagers() {
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
                // 正在滑出的那一页交给动画的收尾动作去隐藏。
                // 这里抢先设 GONE，等于把还没播完的退场动画直接掐掉
                moving && i == old - 1 -> Unit
                else -> v.visibility = View.GONE
            }
        }
        page = target
        if (animate) ThemeFx.slideInPage(pages()[target - 1], forward = target > old)

        binding.txtOnbStep.text = "$target / ${pages().size}"
        binding.btnNext.setText(if (target == pages().size) R.string.onb_start else R.string.onb_next)

        val colors = PaletteColors.of(PaletteId.of(paletteKey), ThemeState.light)
        dots().forEachIndexed { i, dot ->
            val on = i == target - 1
            dot.alpha = if (on) 1f else 0.32f
            dot.backgroundTintList = ColorStateList.valueOf(if (on) colors.accent else colors.textMuted)
        }
    }

    // ---- 第 2 页：Key ----

    private fun setUpKey() {
        binding.etKey.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveKeyIfAny()
        }
    }

    private fun saveKeyIfAny() {
        val value = binding.etKey.text?.toString()?.trim().orEmpty()
        if (value.isEmpty()) return
        lifecycleScope.launch {
            store.setApiKey(value)
            binding.txtKeyState.visibility = View.VISIBLE
            binding.txtKeyState.text = getString(R.string.onb_p2_saved)
        }
    }

    private fun loadCurrent() {
        lifecycleScope.launch {
            val settings = store.current()
            paletteKey = settings.paletteId
            binding.etKey.setText(settings.apiKey)
            if (settings.apiKey.isNotBlank()) {
                binding.txtKeyState.visibility = View.VISIBLE
                binding.txtKeyState.text = getString(R.string.onb_p2_saved)
            }
            refreshAppearanceUi(settings.paletteId, settings.followSystem, settings.lightMode)
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
            refreshAppearanceUi(settings.paletteId, settings.followSystem, settings.lightMode)
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
            // 明暗换的是主题资源，只有重建才有正确的窗口底色
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
    }

    private fun isSystemDark(): Boolean {
        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    // ---- 收尾 ----

    private fun finishTutorial() {
        // 这里原先开了两条协程：一条写 Key，一条写 onboarded 后立刻 finish()。
        // finish() 会取消 lifecycleScope 里没跑完的协程，Key 就被随机丢掉了。
        // 现在串成一条链：写 Key → 写标记 → 关页，顺序确定。
        val key = binding.etKey.text?.toString()?.trim().orEmpty()
        lifecycleScope.launch {
            if (key.isNotEmpty()) store.setApiKey(key)
            store.setOnboarded(true)
            finish()
        }
    }

    companion object {
        private const val KEY_PAGE = "onboarding_page"

        fun intent(context: Context): Intent = Intent(context, OnboardingActivity::class.java)
    }
}