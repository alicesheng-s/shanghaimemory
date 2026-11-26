package com.shanghai.memory.data

import android.content.Context
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.shanghai.memory.data.model.PoiInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 高德搜索 SDK 的协程封装。
 *
 * 类型里必须带上「190000 地名地址」与「150000 交通设施」：站在一条马路或一栋老楼前时，
 * 高德往往只把它归到地名地址，原先的纯「景点/机构」分类查不到，就会退而返回几百米外的商场，
 * 于是出现「地点和地图上显示的对不上」。分类放宽后由距离分级（120 → 400 → 900 米）收敛。
 *
 * 所有 asyn 接口都套了超时：SDK 在无网、未初始化、key 校验失败时可能永不回调，
 * 没有超时的 suspendCancellableCoroutine 会让整条调用链一直挂着。
 */
class AmapService(context: Context) {

    companion object {
        private const val CITY_CODE = "021"
        // 090000 医疗保健服务不能少：站在医院门口时，医院若不在候选池里，
        // 就会被同一栋楼里的公司、门店顶替，出现「地图显示医院、识别成公司」的错位
        private const val POI_TYPES =
            "190000|110000|090000|140000|120000|130000|150000|080000|060000"

        /** 周边检索一次取满：候选池越大，距离分级收敛得越准 */
        private const val NEARBY_PAGE_SIZE = 25

        private const val NEARBY_TIMEOUT_MS = 8000L
        private const val REGEO_TIMEOUT_MS = 6000L
        private const val GEOCODE_TIMEOUT_MS = 4000L
        private const val KEYWORD_TIMEOUT_MS = 8000L
    }

    private val ctx: Context = context.applicationContext

    suspend fun nearby(lat: Double, lng: Double, radius: Int = 900): List<PoiInfo> =
        withContext(Dispatchers.Main) {
            val search = newPoiSearch(NEARBY_PAGE_SIZE)
            search.bound = PoiSearch.SearchBound(LatLonPoint(lat, lng), radius, true)
            val result = withTimeoutOrNull(NEARBY_TIMEOUT_MS) {
                suspendCancellableCoroutine<PoiResult?> { cont ->
                    search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                        override fun onPoiSearched(r: PoiResult?, code: Int) {
                            if (cont.isActive) cont.resume(if (code == 1000) r else null)
                        }

                        override fun onPoiItemSearched(item: com.amap.api.services.core.PoiItem?, code: Int) = Unit
                    })
                    search.searchPOIAsyn()
                }
            }
            result?.pois.orEmpty().mapNotNull { item ->
                val point = item.latLonPoint ?: return@mapNotNull null
                PoiInfo(
                    name = item.title.orEmpty(),
                    type = item.typeDes.orEmpty(),
                    address = item.snippet.orEmpty(),
                    district = districtOf(item),
                    lat = point.latitude,
                    lng = point.longitude,
                    amapId = item.poiId,
                    distanceMeters = item.distance
                )
            }.filter { it.name.isNotBlank() }
        }

    /** 逆地理：拿到「区 + 街道地址」，用于提示卡的第二行 */
    suspend fun regeocode(lat: Double, lng: Double): Pair<String, String> =
        withContext(Dispatchers.Main) {
            val search = GeocodeSearch(ctx)
            val result = withTimeoutOrNull(REGEO_TIMEOUT_MS) {
                suspendCancellableCoroutine<RegeocodeResult?> { cont ->
                    search.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                        override fun onRegeocodeSearched(r: RegeocodeResult?, code: Int) {
                            if (cont.isActive) cont.resume(if (code == 1000) r else null)
                        }

                        override fun onGeocodeSearched(r: com.amap.api.services.geocoder.GeocodeResult?, code: Int) = Unit
                    })
                    search.getFromLocationAsyn(
                        RegeocodeQuery(LatLonPoint(lat, lng), 200f, GeocodeSearch.AMAP)
                    )
                }
            }
            val addr = result?.regeocodeAddress
            val district = addr?.district.orEmpty()
            // 高德 SDK 的方法名是 getFormatAddress()，官方拼写如此
            val formatted = addr?.formatAddress.orEmpty()
            district to formatted
        }

    /** 正向地理编码：把路线站点名换成坐标，画线与向导都靠它；偶发限流自动退避重试一次 */
    suspend fun geocode(keyword: String): Pair<Double, Double>? =
        withContext(Dispatchers.Main) {
            val text = keyword.trim()
            if (text.isEmpty()) return@withContext null
            var (code, result) = geocodeOnce(text)
            if (code != 1000 && shouldRetry(code)) {
                delay(750)
                val retry = geocodeOnce(text)
                code = retry.first
                result = retry.second
            }
            if (code != 1000) return@withContext null
            result?.geocodeAddressList?.firstOrNull()?.latLonPoint?.let { it.latitude to it.longitude }
        }

    private suspend fun geocodeOnce(text: String): Pair<Int, com.amap.api.services.geocoder.GeocodeResult?> {
        val search = GeocodeSearch(ctx)
        return withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                search.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                    override fun onRegeocodeSearched(r: RegeocodeResult?, code: Int) = Unit

                    override fun onGeocodeSearched(
                        r: com.amap.api.services.geocoder.GeocodeResult?,
                        code: Int
                    ) {
                        if (cont.isActive) cont.resume(code to (if (code == 1000) r else null))
                    }
                })
                search.getFromLocationNameAsyn(
                    com.amap.api.services.geocoder.GeocodeQuery(text, CITY_CODE)
                )
            }
        } ?: (0 to null)
    }

    /**
     * 按名字直接找地方。搜索「武康大楼」这类具体地名时周边检索帮不上忙，得走关键词检索。
     * 类型留空表示不限分类：老饭店、旧里弄往往只挂在「餐饮服务」或「地名地址」下面。
     */
    suspend fun searchByKeyword(keyword: String, limit: Int = 12): List<PoiInfo> =
        withContext(Dispatchers.Main) {
            val text = keyword.trim()
            if (text.isEmpty()) return@withContext emptyList()
            var (code, result) = keywordOnce(text, limit)
            if (code != 1000 && shouldRetry(code)) {
                delay(750)
                val retry = keywordOnce(text, limit)
                code = retry.first
                result = retry.second
            }
            if (code != 1000) return@withContext emptyList()
            result?.pois.orEmpty().mapNotNull { item ->
                val point = item.latLonPoint ?: return@mapNotNull null
                PoiInfo(
                    name = item.title.orEmpty(),
                    type = item.typeDes.orEmpty(),
                    address = item.snippet.orEmpty(),
                    district = districtOf(item),
                    lat = point.latitude,
                    lng = point.longitude,
                    amapId = item.poiId,
                    distanceMeters = item.distance
                )
            }.filter { it.name.isNotBlank() }
        }

    private suspend fun keywordOnce(text: String, limit: Int): Pair<Int, PoiResult?> {
        val query = PoiSearch.Query(text, "", CITY_CODE).apply {
            pageSize = limit.coerceIn(1, 25)
            pageNum = 1
        }
        val search = PoiSearch(ctx, query)
        return withTimeoutOrNull(KEYWORD_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                    override fun onPoiSearched(r: PoiResult?, code: Int) {
                        if (cont.isActive) cont.resume(code to (if (code == 1000) r else null))
                    }

                    override fun onPoiItemSearched(
                        item: com.amap.api.services.core.PoiItem?,
                        code: Int
                    ) = Unit
                })
                search.searchPOIAsyn()
            }
        } ?: (0 to null)
    }

    /** 非断网（1804/1806）与非超时（0）场景（含高德 QPS 限流 1004/1009/1013）允许退避重试一次 */
    private fun shouldRetry(code: Int): Boolean = code != 0 && code != 1804 && code != 1806

    /**
     * 行政区用 adName：cityName 是整个「上海市」，放进「区」字段等于没填。
     * 少数条目没有 adName，再退回 cityName 兜底。
     */
    private fun districtOf(item: com.amap.api.services.core.PoiItem): String =
        item.adName.orEmpty().ifBlank { item.cityName.orEmpty() }

    private fun newPoiSearch(pageSize: Int): PoiSearch =
        PoiSearch(ctx, PoiSearch.Query("", POI_TYPES, CITY_CODE).apply {
            this.pageSize = pageSize.coerceIn(1, 25)
            pageNum = 1
        })
}
