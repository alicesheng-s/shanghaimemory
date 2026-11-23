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
 */
class AmapService(context: Context) {

    companion object {
        private const val CITY_CODE = "021"
        // 090000 医疗保健服务不能少：站在医院门口时，医院若不在候选池里，
        // 就会被同一栋楼里的公司、门店顶替，出现「地图显示医院、识别成公司」的错位
        private const val POI_TYPES =
            "190000|110000|090000|140000|120000|130000|150000|080000|060000"
    }

    private val ctx: Context = context.applicationContext

    suspend fun nearby(lat: Double, lng: Double, radius: Int = 900): List<PoiInfo> =
        withContext(Dispatchers.Main) {
            val query = PoiSearch.Query("", POI_TYPES, CITY_CODE).apply {
                pageSize = 25
                pageNum = 1
            }
            newPoiSearch().let { search ->
                search.bound = PoiSearch.SearchBound(LatLonPoint(lat, lng), radius, true)
                val result = suspendCancellableCoroutine<PoiResult?> { cont ->
                    search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                        override fun onPoiSearched(r: PoiResult?, code: Int) {
                            if (cont.isActive) cont.resume(if (code == 1000) r else null)
                        }

                        override fun onPoiItemSearched(item: com.amap.api.services.core.PoiItem?, code: Int) = Unit
                    })
                    search.searchPOIAsyn()
                }
                val pois = result?.pois.orEmpty().mapNotNull { item ->
                    val point = item.latLonPoint ?: return@mapNotNull null
                    PoiInfo(
                        name = item.title.orEmpty(),
                        type = item.typeDes.orEmpty(),
                        address = item.snippet.orEmpty(),
                        district = item.cityName.orEmpty(),
                        lat = point.latitude,
                        lng = point.longitude,
                        amapId = item.poiId,
                        distanceMeters = item.distance
                    )
                }.filter { it.name.isNotBlank() }
                pois
            }
        }

    /** 逆地理：拿到「区 + 街道地址」，用于提示卡的第二行 */
    suspend fun regeocode(lat: Double, lng: Double): Pair<String, String> =
        withContext(Dispatchers.Main) {
            val search = GeocodeSearch(ctx)
            val result = suspendCancellableCoroutine<RegeocodeResult?> { cont ->
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
            val addr = result?.regeocodeAddress
            val district = addr?.district.orEmpty()
            // 高德 SDK 的方法名是 getFormatAddress()，官方拼写如此
            val formatted = addr?.formatAddress.orEmpty()
            district to formatted
        }

    /** 正向地理编码：把路线站点名换成坐标，画线与向导都靠它 */
    suspend fun geocode(keyword: String): Pair<Double, Double>? =
        withContext(Dispatchers.Main) {
            val text = keyword.trim()
            if (text.isEmpty()) return@withContext null
            val search = GeocodeSearch(ctx)
            val result = withTimeoutOrNull(4000) {
                suspendCancellableCoroutine<com.amap.api.services.geocoder.GeocodeResult?> { cont ->
                    search.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                        override fun onRegeocodeSearched(r: RegeocodeResult?, code: Int) = Unit

                        override fun onGeocodeSearched(
                            r: com.amap.api.services.geocoder.GeocodeResult?,
                            code: Int
                        ) {
                            if (cont.isActive) cont.resume(if (code == 1000) r else null)
                        }
                    })
                    search.getFromLocationNameAsyn(
                        com.amap.api.services.geocoder.GeocodeQuery(text, CITY_CODE)
                    )
                }
            }
            result?.geocodeAddressList?.firstOrNull()?.latLonPoint?.let { it.latitude to it.longitude }
        }

    /**
     * 按名字直接找地方。搜索「武康大楼」这类具体地名时周边检索帮不上忙，得走关键词检索。
     * 类型留空表示不限分类：老饭店、旧里弄往往只挂在「餐饮服务」或「地名地址」下面。
     */
    suspend fun searchByKeyword(keyword: String, limit: Int = 12): List<PoiInfo> =
        withContext(Dispatchers.Main) {
            val text = keyword.trim()
            if (text.isEmpty()) return@withContext emptyList()
            val query = PoiSearch.Query(text, "", CITY_CODE).apply {
                pageSize = limit.coerceIn(1, 25)
                pageNum = 1
            }
            val search = PoiSearch(ctx, query)
            val result = withTimeoutOrNull(8000) {
                suspendCancellableCoroutine<PoiResult?> { cont ->
                    search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                        override fun onPoiSearched(r: PoiResult?, code: Int) {
                            if (cont.isActive) cont.resume(if (code == 1000) r else null)
                        }

                        override fun onPoiItemSearched(
                            item: com.amap.api.services.core.PoiItem?,
                            code: Int
                        ) = Unit
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
                    district = item.cityName.orEmpty(),
                    lat = point.latitude,
                    lng = point.longitude,
                    amapId = item.poiId,
                    distanceMeters = item.distance
                )
            }.filter { it.name.isNotBlank() }
        }

    private fun newPoiSearch(): PoiSearch = PoiSearch(ctx, PoiSearch.Query("", POI_TYPES, CITY_CODE))}
