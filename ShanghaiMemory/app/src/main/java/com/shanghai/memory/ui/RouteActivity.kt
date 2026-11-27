package com.shanghai.memory.ui

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shanghai.memory.R
import com.shanghai.memory.data.AmapService
import com.shanghai.memory.data.CuratedSpot
import com.shanghai.memory.data.CuratedSpots
import com.shanghai.memory.data.LocationHolder
import com.shanghai.memory.data.RouteRepository
import com.shanghai.memory.data.SettingsStore
import com.shanghai.memory.data.SpotCandidate
import com.shanghai.memory.data.SpotTheme
import com.shanghai.memory.databinding.ActivityRouteBinding
import com.shanghai.memory.ui.theme.ThemeFx
import com.shanghai.memory.ui.theme.ThemeState
import com.shanghai.memory.ui.theme.ThemedActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 游览路线的入口。
 *
 * 三层：先选「预设」还是「自定义」；预设再选主题；最后都落到同一个
 * 「勾几个地方」的界面，勾完才进对话让 AI 排先后与停留时间。
 * 之所以把选点单独抽一层，是因为以前直接拿两条写死的路线去生成，
 * 用户既不知道路上有什么，也没法换。
 */
class RouteActivity : ThemedActivity() {

    private lateinit var binding: ActivityRouteBinding
    private lateinit var store: SettingsStore
    private lateinit var amap: AmapService
    private lateinit var repo: RouteRepository
    private lateinit var adapter: SpotAdapter

    private val selected = LinkedHashSet<String>()
    private var spots: List<SpotCandidate> = emptyList()
    private var page = PAGE_MENU
    private var themeKey: String? = null
    private var themeTitle = ""
    /** 选点页是从哪一层进来的，返回时回到原地 */
    private var spotsFrom = PAGE_PRESET
    private var preference = PREF_BOTH
    private var userLat = 0.0
    private var userLng = 0.0
    private var workJob: Job? = null
    private var locationClient: AMapLocationClient? = null
    private var customPresets: List<SpotTheme> = emptyList()
    private val subPageBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showPage(parentOf(page))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, subPageBackCallback)
        binding = ActivityRouteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = SettingsStore(this)
        amap = AmapService(this)
        repo = RouteRepository(store, amap = amap)

        adapter = SpotAdapter { spot -> toggle(spot) }
        binding.listSpots.layoutManager = GridLayoutManager(this, 2)
        binding.listSpots.adapter = adapter
        binding.listSpots.itemAnimator = null

        binding.btnBack.setOnClickListener {
            if (page == PAGE_MENU) finish() else showPage(parentOf(page))
        }

        binding.menuPreset.setOnClickListener { showPage(PAGE_PRESET) }
        binding.menuCustom.setOnClickListener { showPage(PAGE_CUSTOM) }

        binding.themePower.setOnClickListener { openTheme(CuratedSpots.themes[0]) }
        binding.themeIndustry.setOnClickListener { openTheme(CuratedSpots.themes[1]) }

        binding.prefEvent.setOnClickListener { pickPreference(PREF_EVENT) }
        binding.prefHistory.setOnClickListener { pickPreference(PREF_HISTORY) }
        binding.prefBoth.setOnClickListener { pickPreference(PREF_BOTH) }
        highlightPreference()

        binding.btnFindSpots.setOnClickListener { findCustomSpots() }
        binding.btnUseSpots.setOnClickListener { useSelected() }
        binding.btnAddPreset.setOnClickListener { showCreatePresetDialog() }
        binding.btnSavePreset.setOnClickListener { showSaveSearchAsPresetDialog() }

        loadCustomPresets()
        if (savedInstanceState != null) {
            restoreUiState(savedInstanceState)
        } else {
            updateUseButton()
            showPage(PAGE_MENU, animate = false)
        }
    }

    private fun restoreUiState(saved: Bundle) {
        preference = saved.getString(KEY_PREF, PREF_BOTH).orEmpty().ifBlank { PREF_BOTH }
        highlightPreference()
        spotsFrom = saved.getInt(KEY_SPOTS_FROM, PAGE_PRESET)
        themeKey = saved.getString(KEY_THEME_KEY)
        themeTitle = saved.getString(KEY_THEME_TITLE).orEmpty()
        selected.clear()
        selected.addAll(saved.getStringArrayList(KEY_SELECTED).orEmpty())

        val targetPage = saved.getInt(KEY_PAGE, PAGE_MENU)
        if (targetPage == PAGE_SPOTS && spotsFrom == PAGE_PRESET) {
            val builtIn = CuratedSpots.byKey(themeKey)
            if (builtIn != null) {
                openRestoredTheme(builtIn)
                return
            }
            lifecycleScope.launch {
                val allCustom = store.getCustomPresets()
                customPresets = allCustom
                renderCustomPresets()
                val customTheme = allCustom.firstOrNull { it.key == themeKey }
                if (customTheme != null) {
                    openRestoredTheme(customTheme)
                } else {
                    updateUseButton()
                    showPage(PAGE_PRESET, animate = false)
                }
            }
            return
        }
        if (targetPage == PAGE_SPOTS) {
            // 自定义检索结果未持久化，重建后回到自定义输入页避免空列表
            showPage(PAGE_CUSTOM, animate = false)
            updateUseButton()
            return
        }
        updateUseButton()
        showPage(targetPage, animate = false)
    }

    private fun openRestoredTheme(theme: SpotTheme) {
        themeTitle = theme.title
        spots = theme.spots.map { SpotCandidate.of(it) }
        showPage(PAGE_SPOTS, animate = false)
        binding.txtSpotsHint.text = getString(R.string.route_spots_count, spots.size)
        renderSpots()
        ensureLocation { locate() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_PAGE, page)
        outState.putInt(KEY_SPOTS_FROM, spotsFrom)
        outState.putString(KEY_THEME_KEY, themeKey)
        outState.putString(KEY_THEME_TITLE, themeTitle)
        outState.putString(KEY_PREF, preference)
        outState.putStringArrayList(KEY_SELECTED, ArrayList(selected))
        super.onSaveInstanceState(outState)
    }

    // ---- 页面切换 ----

    private fun showPage(target: Int, animate: Boolean = true) {
        val old = page
        val views = mapOf(
            PAGE_MENU to binding.pageMenu,
            PAGE_PRESET to binding.pagePreset,
            PAGE_CUSTOM to binding.pageCustom,
            PAGE_SPOTS to binding.pageSpots,
        )
        if (animate && target != old) {
            views[old]?.let { ThemeFx.slideOutPage(it, forward = target > old) }
        }
        views.forEach { (key, view) ->
            when {
                key == target -> view.visibility = View.VISIBLE
                // 正在退出的那一页交给动画收尾，这里设 GONE 会把退场动画掐断
                animate && target != old && key == old -> Unit
                else -> view.visibility = View.GONE
            }
        }
        page = target
        subPageBackCallback.isEnabled = target != PAGE_MENU
        binding.btnAddPreset.visibility = if (target == PAGE_PRESET) View.VISIBLE else View.GONE
        binding.btnSavePreset.visibility =
            if (target == PAGE_SPOTS && spotsFrom == PAGE_CUSTOM) View.VISIBLE else View.GONE
        if (target != PAGE_CUSTOM) hideKeyboard()
        binding.txtRouteTitle.text = when (target) {
            PAGE_PRESET -> getString(R.string.route_preset_title)
            PAGE_CUSTOM -> getString(R.string.route_custom_title)
            PAGE_SPOTS -> themeTitle.ifBlank { getString(R.string.route_pick_title) }
            else -> getString(R.string.route_pick_title)
        }
        if (animate) views[target]?.let { ThemeFx.slideInPage(it, forward = target > old) }
    }

    private fun hideKeyboard() {
        binding.etRouteKeywords.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
    private fun parentOf(current: Int): Int = when (current) {
        PAGE_SPOTS -> spotsFrom
        PAGE_CUSTOM, PAGE_PRESET -> PAGE_MENU
        else -> PAGE_MENU
    }

    // ---- 预设主题 ----

    private fun openTheme(theme: SpotTheme) {
        themeKey = theme.key
        themeTitle = theme.title
        spotsFrom = PAGE_PRESET
        selected.clear()
        spots = theme.spots.map { SpotCandidate.of(it) }
        showPage(PAGE_SPOTS)
        binding.txtSpotsHint.text = getString(R.string.route_spots_count, spots.size)
        renderSpots()
        ensureLocation { locate() }
    }

    private fun loadCustomPresets() {
        lifecycleScope.launch {
            customPresets = store.getCustomPresets()
            renderCustomPresets()
        }
    }

    private fun renderCustomPresets() {
        val box = binding.customPresetsContainer
        box.removeAllViews()
        val colors = ThemeState.colors
        val dp = resources.displayMetrics.density
        customPresets.forEach { theme ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                val pad = (18 * dp).roundToInt()
                setPadding(pad, pad, pad, pad)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (12 * dp).roundToInt() }
                setOnClickListener { openTheme(theme) }
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val titleView = TextView(this).apply {
                text = theme.title
                setTextColor(colors.text)
                textSize = 15.5f
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            val delView = TextView(this).apply {
                text = getString(R.string.route_custom_preset_delete)
                setTextColor(colors.textMuted)
                textSize = 12.5f
                val hPad = (8 * dp).roundToInt()
                val vPad = (4 * dp).roundToInt()
                setPadding(hPad, vPad, 0, vPad)
                setOnClickListener { confirmDeletePreset(theme) }
            }
            topRow.addView(titleView)
            topRow.addView(delView)

            val descView = TextView(this).apply {
                text = theme.subtitle.ifBlank {
                    getString(R.string.route_custom_preset_default_sub, theme.spots.size)
                }
                setTextColor(colors.textMuted)
                textSize = 13f
                setLineSpacing(0f, 1.45f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (6 * dp).roundToInt() }
            }

            card.addView(topRow)
            card.addView(descView)
            box.addView(card)
        }
        applyAppearance(box)
    }

    private fun confirmDeletePreset(theme: SpotTheme) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.route_custom_preset_delete_title)
            .setMessage(getString(R.string.route_custom_preset_delete_msg, theme.title))
            .setPositiveButton(R.string.route_custom_preset_delete) { _, _ ->
                lifecycleScope.launch {
                    customPresets = store.deleteCustomPreset(theme.key)
                    renderCustomPresets()
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showCreatePresetDialog() {
        val colors = ThemeState.colors
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).roundToInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).roundToInt(), (8 * dp).roundToInt(), (20 * dp).roundToInt(), 0)
        }

        fun makeLabel(resId: Int, topDp: Int = 0) = TextView(this).apply {
            setText(resId)
            setTextColor(colors.textMuted)
            textSize = 12.5f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (topDp * dp).roundToInt() }
        }

        fun makeInput(hintRes: Int, lines: Int = 1) = EditText(this).apply {
            setHint(hintRes)
            setTextColor(colors.text)
            setHintTextColor(colors.textMuted)
            setBackgroundResource(R.drawable.bg_card)
            setPadding(pad, (12 * dp).roundToInt(), pad, (12 * dp).roundToInt())
            textSize = 14.5f
            if (lines > 1) {
                minLines = lines
                maxLines = 6
                gravity = Gravity.TOP or Gravity.START
            } else {
                isSingleLine = true
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (6 * dp).roundToInt() }
        }

        val etName = makeInput(R.string.route_custom_preset_name_hint)
        val etDesc = makeInput(R.string.route_custom_preset_desc_hint)
        val etSpots = makeInput(R.string.route_custom_preset_spots_hint, lines = 4)

        box.addView(makeLabel(R.string.route_custom_preset_name))
        box.addView(etName)
        box.addView(makeLabel(R.string.route_custom_preset_desc, topDp = 12))
        box.addView(etDesc)
        box.addView(makeLabel(R.string.route_custom_preset_spots, topDp = 12))
        box.addView(etSpots)
        applyAppearance(box)

        val scroller = ScrollView(this).apply { addView(box) }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.route_custom_preset_title)
            .setView(scroller)
            .setPositiveButton(R.string.common_confirm, null)
            .setNegativeButton(R.string.common_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                val rawNames = etSpots.text.toString()
                    .split('\n', ',', '，', '、', ';', '；')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                if (title.isEmpty() || rawNames.isEmpty()) {
                    Toast.makeText(this, R.string.route_custom_preset_need_input, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val defaultArea = if (ThemeState.isEnglish) "Shanghai" else "上海"
                val defaultBlurb = if (ThemeState.isEnglish) "Custom preset stop" else "自定义预设点位"
                val curated = rawNames.map { name ->
                    CuratedSpot(name = name, area = defaultArea, blurb = defaultBlurb, searchKey = name)
                }
                val theme = SpotTheme(
                    key = "user_${System.currentTimeMillis()}",
                    title = title,
                    subtitle = desc.ifBlank { getString(R.string.route_custom_preset_default_sub, curated.size) },
                    spots = curated
                )
                dialog.dismiss()
                lifecycleScope.launch {
                    customPresets = store.saveCustomPreset(theme)
                    renderCustomPresets()
                    Toast.makeText(
                        this@RouteActivity,
                        getString(R.string.route_custom_preset_saved, title),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        dialog.show()
    }

    private fun showSaveSearchAsPresetDialog() {
        val chosen = if (selected.isNotEmpty()) {
            spots.filter { selected.contains(it.name) }
        } else {
            spots
        }
        if (chosen.isEmpty()) return

        val colors = ThemeState.colors
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).roundToInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).roundToInt(), (8 * dp).roundToInt(), (20 * dp).roundToInt(), 0)
        }
        val hintView = TextView(this).apply {
            text = if (selected.isNotEmpty()) {
                getString(R.string.route_save_preset_selected_hint, chosen.size)
            } else {
                getString(R.string.route_save_preset_all_hint, chosen.size)
            }
            setTextColor(colors.textMuted)
            textSize = 13f
        }
        val etName = EditText(this).apply {
            setHint(R.string.route_custom_preset_name_hint)
            setText(themeTitle)
            setTextColor(colors.text)
            setHintTextColor(colors.textMuted)
            setBackgroundResource(R.drawable.bg_card)
            setPadding(pad, (12 * dp).roundToInt(), pad, (12 * dp).roundToInt())
            textSize = 15f
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * dp).roundToInt() }
        }
        box.addView(hintView)
        box.addView(etName)
        applyAppearance(box)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.route_save_preset_dialog_title)
            .setView(box)
            .setPositiveButton(R.string.common_confirm, null)
            .setNegativeButton(R.string.common_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = etName.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(this, R.string.route_custom_preset_name_hint, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val curated = chosen.map { s ->
                    CuratedSpot(
                        name = s.name,
                        area = s.area,
                        blurb = s.blurb,
                        searchKey = s.searchKey.ifBlank { s.name }
                    )
                }
                val theme = SpotTheme(
                    key = "user_${System.currentTimeMillis()}",
                    title = title,
                    subtitle = getString(R.string.route_custom_preset_default_sub, curated.size),
                    spots = curated
                )
                dialog.dismiss()
                lifecycleScope.launch {
                    customPresets = store.saveCustomPreset(theme)
                    renderCustomPresets()
                    Toast.makeText(
                        this@RouteActivity,
                        getString(R.string.route_custom_preset_saved, title),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        dialog.show()
    }

    // ---- 自定义 ----

    private fun pickPreference(value: String) {
        preference = value
        highlightPreference()
    }

    private fun highlightPreference() {
        val colors = com.shanghai.memory.ui.theme.ThemeState.colors
        listOf(
            binding.prefEvent to PREF_EVENT,
            binding.prefHistory to PREF_HISTORY,
            binding.prefBoth to PREF_BOTH,
        ).forEach { (view, key) ->
            val on = key == preference
            view.setTextColor(if (on) colors.onAccent else colors.text)
            val bg = view.background?.mutate()
            if (bg is android.graphics.drawable.GradientDrawable) {
                bg.setColor(if (on) colors.accent else colors.card)
            }
        }
    }

    private fun findCustomSpots() {
        val keywords = binding.etRouteKeywords.text.toString().trim()
        if (keywords.isBlank()) {
            binding.txtCustomStatus.text = getString(R.string.route_custom_need_keywords)
            return
        }
        binding.btnFindSpots.isEnabled = false
        binding.txtCustomStatus.text = getString(R.string.route_custom_searching)
        workJob?.cancel()
        workJob = lifecycleScope.launch {
            try {
                val found = repo.searchSpots(keywords, preference, want = 10)
                if (found.isEmpty()) {
                    binding.txtCustomStatus.text = getString(R.string.route_custom_empty)
                    return@launch
                }
                themeKey = null
                themeTitle = keywords.take(12)
                spotsFrom = PAGE_CUSTOM
                selected.clear()
                spots = found
                binding.txtCustomStatus.text = ""
                showPage(PAGE_SPOTS)
                binding.txtSpotsHint.text = getString(R.string.route_spots_count, spots.size)
                renderSpots()
                ensureLocation { locate() }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                binding.txtCustomStatus.text = e.message ?: getString(R.string.route_custom_empty)
            } finally {
                binding.btnFindSpots.isEnabled = true
            }
        }
    }

    // ---- 定位与距离 ----

    private fun ensureLocation(then: () -> Unit) {
        if (LocationHolder.hasFix) {
            userLat = LocationHolder.lat
            userLng = LocationHolder.lng
            then()
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQ_LOCATION
            )
            then()
            return
        }
        val client = AMapLocationClient(applicationContext)
        val option = AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            isOnceLocation = true
            isNeedAddress = false
        }
        client.setLocationOption(option)
        client.setLocationListener { location ->
            if (location != null && location.errorCode == 0) {
                userLat = location.latitude
                userLng = location.longitude
                LocationHolder.update(userLat, userLng)
            }
            runCatching { client.stopLocation() }
            runCatching { client.onDestroy() }
            locationClient = null
            then()
        }
        locationClient = client
        client.startLocation()
    }

    private fun locate() {
        val snapshot = spots
        if (snapshot.isEmpty()) return
        workJob?.cancel()
        workJob = lifecycleScope.launch {
            val located = repo.locateSpots(snapshot, userLat, userLng)
            if (located != spots) {
                spots = located
                renderSpots()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            ensureLocation { locate() }
        }
    }

    // ---- 选点 ----

    private fun renderSpots() {
        adapter.submitList(spots.map { SpotRow(it, selected.contains(it.name)) })
        updateUseButton()
    }

    private fun toggle(spot: SpotCandidate) {
        if (!selected.remove(spot.name)) selected.add(spot.name)
        renderSpots()
    }

    private fun updateUseButton() {
        binding.btnUseSpots.alpha = if (selected.isEmpty()) 0.45f else 1f
        binding.btnUseSpots.isEnabled = selected.isNotEmpty()
        binding.btnUseSpots.text = if (selected.isEmpty()) {
            getString(R.string.route_use_spots_empty)
        } else {
            getString(R.string.route_use_spots, selected.size)
        }
    }

    private fun useSelected() {
        if (selected.isEmpty()) return
        val name = themeTitle.ifBlank { getString(R.string.route_pick_title) }
        startActivity(ChatActivity.planIntent(this, name, selected.toList()))
    }

    override fun onDestroy() {
        workJob?.cancel()
        runCatching { locationClient?.stopLocation() }
        runCatching { locationClient?.onDestroy() }
        locationClient = null
        super.onDestroy()
    }

    private companion object {
        const val PAGE_MENU = 0
        const val PAGE_PRESET = 1
        const val PAGE_CUSTOM = 2
        const val PAGE_SPOTS = 3
        const val REQ_LOCATION = 2001

        const val PREF_EVENT = "活动"
        const val PREF_HISTORY = "历史建筑"
        const val PREF_BOTH = "都行"

        const val KEY_PAGE = "route_page"
        const val KEY_SPOTS_FROM = "route_spots_from"
        const val KEY_THEME_KEY = "route_theme_key"
        const val KEY_THEME_TITLE = "route_theme_title"
        const val KEY_PREF = "route_pref"
        const val KEY_SELECTED = "route_selected"
    }
}
