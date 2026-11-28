package com.shanghai.memory.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shanghai.memory.R
import com.shanghai.memory.data.AmapService
import com.shanghai.memory.data.FamousLandmark
import com.shanghai.memory.data.FamousLandmarks
import com.shanghai.memory.data.LocationHolder
import com.shanghai.memory.data.RouteRepository
import com.shanghai.memory.data.SettingsStore
import com.shanghai.memory.data.model.PoiInfo
import com.shanghai.memory.databinding.ActivityFamousLandmarksBinding
import com.shanghai.memory.ui.theme.AppFonts
import com.shanghai.memory.ui.theme.ThemeState
import com.shanghai.memory.ui.theme.ThemedActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 著名地标页：离线内置 12 处上海知名地标及详尽介绍，样式与游览路线选点一致。
 */
class FamousLandmarksActivity : ThemedActivity() {

    private lateinit var binding: ActivityFamousLandmarksBinding
    private lateinit var store: SettingsStore
    private lateinit var amap: AmapService
    private lateinit var repo: RouteRepository
    private lateinit var adapter: FamousLandmarkAdapter

    private var rows: List<FamousLandmarkRow> = emptyList()
    private var userLat = 0.0
    private var userLng = 0.0
    private var locateJob: Job? = null
    private var locationClient: AMapLocationClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFamousLandmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = SettingsStore(this)
        amap = AmapService(this)
        repo = RouteRepository(store, amap = amap)

        adapter = FamousLandmarkAdapter { row -> showDetail(row) }
        binding.listLandmarks.layoutManager = GridLayoutManager(this, 2)
        binding.listLandmarks.adapter = adapter
        binding.listLandmarks.itemAnimator = null

        binding.btnBack.setOnClickListener { finish() }

        initRows()
        ensureLocation { locate() }
    }

    private fun initRows() {
        val en = ThemeState.isEnglish
        val hasFix = LocationHolder.hasFix
        if (hasFix) {
            userLat = LocationHolder.lat
            userLng = LocationHolder.lng
        }
        rows = FamousLandmarks.all.map { lm ->
            val base = lm.toCandidate(en).locatedAt(lm.fallbackLat, lm.fallbackLng)
            val candidate = if (hasFix) base.withDistance(userLat, userLng) else base
            FamousLandmarkRow(lm, candidate)
        }
        adapter.submitList(rows)
    }

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
        val snapshot = rows
        if (snapshot.isEmpty()) return
        locateJob?.cancel()
        locateJob = lifecycleScope.launch {
            try {
                // 先按各自在线或兜底坐标算出距离，再后台尝试实时校准高德精确坐标
                val en = ThemeState.isEnglish
                val unlocatedInputs = snapshot.map { it.landmark.toCandidate(en) }
                val located = repo.locateSpots(unlocatedInputs, userLat, userLng)
                rows = snapshot.mapIndexed { idx, row ->
                    val lm = row.landmark
                    val resolved = located.getOrNull(idx)
                    val merged = if (resolved != null && resolved.located) {
                        resolved
                    } else {
                        val fallback = lm.toCandidate(en).locatedAt(lm.fallbackLat, lm.fallbackLng)
                        if (userLat != 0.0 || userLng != 0.0) fallback.withDistance(userLat, userLng) else fallback
                    }
                    row.copy(candidate = merged)
                }
                adapter.submitList(rows)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                // 离线场景下已有内置坐标和完整介绍，无需报错
            }
        }
    }

    private fun showDetail(row: FamousLandmarkRow) {
        val en = ThemeState.isEnglish
        val lm = row.landmark
        val spot = row.candidate
        val colors = ThemeState.colors
        val dp = resources.displayMetrics.density
        val pad = (20 * dp).roundToInt()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (8 * dp).roundToInt(), pad, (4 * dp).roundToInt())
        }
        val meta = buildString {
            append(lm.area(en))
            if (spot.distanceText.isNotBlank()) {
                append(" · ")
                append(if (en) "${spot.distanceText} away" else "距你约 ${spot.distanceText}")
            }
        }
        val txtMeta = TextView(this).apply {
            text = meta
            setTextColor(colors.accentBright)
            textSize = 13f
        }
        val txtDetail = TextView(this).apply {
            text = lm.detail(en)
            setTextColor(colors.text)
            textSize = 15f * ThemeState.fontScale
            setLineSpacing(0f, 1.45f)
            typeface = AppFonts.of(this@FamousLandmarksActivity)
                .get(ThemeState.font, ThemeState.importedFontPath)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * dp).roundToInt() }
        }
        box.addView(txtMeta)
        box.addView(txtDetail)

        val maxScrollH = (resources.displayMetrics.heightPixels * 0.44f).roundToInt()
        val scroller = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val capped = MeasureSpec.makeMeasureSpec(maxScrollH, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, capped)
            }
        }.apply { addView(box) }

        val lat = if (spot.located) spot.lat else lm.fallbackLat
        val lng = if (spot.located) spot.lng else lm.fallbackLng

        lateinit var dialog: androidx.appcompat.app.AlertDialog

        fun pillBg(fill: Int, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
            cornerRadius = 12 * dp
            setColor(fill)
            if (stroke != null) setStroke((1 * dp).roundToInt().coerceAtLeast(1), stroke)
        }

        val btnLocate = TextView(this).apply {
            text = getString(R.string.famous_locate_map)
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(colors.text)
            background = pillBg(colors.bgRaise, colors.line)
            setPadding((10 * dp).roundToInt(), (11 * dp).roundToInt(), (10 * dp).roundToInt(), (11 * dp).roundToInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (10 * dp).roundToInt()
            }
            setOnClickListener {
                dialog.dismiss()
                setResult(
                    RESULT_OK,
                    Intent().apply {
                        putExtra(EXTRA_NAME, lm.name(en))
                        putExtra(EXTRA_AREA, lm.area(en))
                        putExtra(EXTRA_QUERY, lm.searchKey)
                        putExtra(EXTRA_LAT, lat)
                        putExtra(EXTRA_LNG, lng)
                    }
                )
                finish()
            }
        }

        val btnAskAi = TextView(this).apply {
            text = getString(R.string.famous_ask_ai)
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(colors.onAccent)
            background = pillBg(colors.accent)
            setPadding((10 * dp).roundToInt(), (11 * dp).roundToInt(), (10 * dp).roundToInt(), (11 * dp).roundToInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                dialog.dismiss()
                val poi = PoiInfo(
                    name = lm.name(en),
                    type = if (en) "Shanghai Landmark" else "上海著名地标",
                    address = lm.area(en),
                    district = lm.area(en).substringBefore("·").trim(),
                    lat = lat,
                    lng = lng,
                    distanceMeters = if (spot.distanceMeters > 0f) spot.distanceMeters.roundToInt() else 0
                )
                startActivity(ChatActivity.storyIntent(this@FamousLandmarksActivity, poi))
            }
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, (12 * dp).roundToInt(), pad, (4 * dp).roundToInt())
            addView(btnLocate)
            addView(btnAskAi)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroller)
            addView(actions)
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(lm.name(en))
            .setView(container)
            .setNegativeButton(R.string.famous_close, null)
            .create()
        dialog.show()
    }

    override fun onDestroy() {
        locateJob?.cancel()
        runCatching { locationClient?.stopLocation() }
        runCatching { locationClient?.onDestroy() }
        locationClient = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_NAME = "landmark_name"
        const val EXTRA_AREA = "landmark_area"
        const val EXTRA_QUERY = "landmark_query"
        const val EXTRA_LAT = "landmark_lat"
        const val EXTRA_LNG = "landmark_lng"
    }
}
