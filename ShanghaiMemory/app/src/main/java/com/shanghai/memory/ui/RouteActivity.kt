package com.shanghai.memory.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.shanghai.memory.R
import com.shanghai.memory.data.AmapService
import com.shanghai.memory.data.CuratedSpots
import com.shanghai.memory.data.LocationHolder
import com.shanghai.memory.data.RouteRepository
import com.shanghai.memory.data.SettingsStore
import com.shanghai.memory.data.SpotCandidate
import com.shanghai.memory.databinding.ActivityRouteBinding
import com.shanghai.memory.ui.theme.ThemeFx
import com.shanghai.memory.ui.theme.ThemedActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    private var themeTitle = ""
    /** 选点页是从哪一层进来的，返回时回到原地 */
    private var spotsFrom = PAGE_PRESET
    private var preference = PREF_BOTH
    private var userLat = 0.0
    private var userLng = 0.0
    private var workJob: Job? = null
    private var locationClient: AMapLocationClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        updateUseButton()
        showPage(PAGE_MENU, animate = false)
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
        if (target != PAGE_CUSTOM) hideKeyboard()
        binding.txtRouteTitle.text = when (target) {
            PAGE_PRESET -> getString(R.string.route_preset_title)
            PAGE_CUSTOM -> getString(R.string.route_custom_title)
            PAGE_SPOTS -> themeTitle.ifBlank { getString(R.string.route_pick_title) }
            else -> getString(R.string.route_pick_title)
        }
        if (animate) views[target]?.let { ThemeFx.slideInPage(it, forward = target > old) }
    }

    /** 系统返回键按层级退：子页回上一级，一级菜单才真的退出 */
    override fun onBackPressed() {
        if (page == PAGE_MENU) super.onBackPressed() else showPage(parentOf(page))
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

    private fun openTheme(theme: com.shanghai.memory.data.SpotTheme) {
        themeTitle = theme.title
        spotsFrom = PAGE_PRESET
        selected.clear()
        spots = theme.spots.map { SpotCandidate.of(it) }
        showPage(PAGE_SPOTS)
        binding.txtSpotsHint.text = getString(R.string.route_spots_count, spots.size)
        renderSpots()
        ensureLocation { locate() }
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
                themeTitle = keywords.take(12)
                spotsFrom = PAGE_CUSTOM
                selected.clear()
                spots = found
                binding.txtCustomStatus.text = ""
                showPage(PAGE_SPOTS)
                binding.txtSpotsHint.text = getString(R.string.route_spots_count, spots.size)
                renderSpots()
                ensureLocation { locate() }
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
    }
}
