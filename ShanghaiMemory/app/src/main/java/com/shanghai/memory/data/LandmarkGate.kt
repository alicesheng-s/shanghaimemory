package com.shanghai.memory.data

import com.shanghai.memory.data.model.PoiInfo

/**
 * 判断游标下的这个 POI 值不值得讲一段城市记忆。
 *
 * 真机实测暴露的问题：站在自家小区门口，高德返回最近的 POI 是「尚品电动车（临汾名城北区店）」，
 * 大模型照样老老实实联网检索，然后把这家车行的工商注册地址当成「历史」讲了出来。
 * 入口不筛，模型再准也会跑偏——所以这里加一道闸门。
 *
 * 判定顺序：门店后缀 → 自定义关键词 → 永远拒绝的词 → 严格模式 → 商业词 → 公共机构类型
 * → 历史词 → 楼盘词 → 类型。
 * 「永远拒绝」必须排在严格模式之前：否则「静安寺地铁站」这类名字含 [STRICT_HINTS] 里「寺」的
 * 噪音点，一进严格模式就被当成老建筑放行。
 * 每一档都由 [GatePolicy] 决定松紧，默认配置等价于 0.3.3 的行为。
 */
object LandmarkGate {

    /** 无论设置怎么调都不该被当成「城市记忆」的地方：日常消费、办事、纯噪音 */
    private val BLOCK_ALWAYS = listOf(
        "便利店", "超市", "洗衣", "干洗", "理发", "美发", "菜鸟", "快递", "驿站",
        "停车", "公厕", "厕所", "ATM", "照相", "锁业", "搬家", "保洁",
        "小吃", "快餐", "奶茶", "咖啡", "火锅", "烧烤", "面馆", "饭馆", "食堂",
        "蔬菜", "水果", "药店", "牙科", "口腔", "诊所", "门诊", "宠物", "母婴",
        "汽修", "洗车", "加油", "充电桩", "足疗", "按摩", "养生", "棋牌", "网咖",
        "托育", "早教", "月子", "家政", "保姆", "看护", "网格", "驾校", "党校",
        "服务站", "政务", "派出所", "警务室", "居委会", "党群", "城管", "消防站",
        "监督所", "工商所", "税务", "街道办", "办事处", "管理局", "服务中心",
        // 现代路口与立交没有记忆，只有坐标
        "交叉口", "路口", "立交", "交叉", "出入口", "闸口", "隧道", "高架",
        // 交通节点同理：「静安寺地铁站」不是静安寺
        "地铁", "公交站", "公交枢纽", "轮渡站"
    )

    /** 商业主体词：打开「公司门店也算」之后才放行 */
    private val BLOCK_BUSINESS = listOf(
        "有限公司", "有限责任公司", "股份有限公司", "分公司", "集团", "经营部", "商行",
        "门市", "工作室", "事务所", "银行网点", "营业厅", "售楼", "中介", "房产",
        "装饰", "图文", "广告", "打印", "五金", "建材", "灯具", "窗帘", "家具",
        "电器", "手机", "通讯", "电动车", "自行车", "摩托车", "美容", "SPA",
        "会所", "足浴", "洗浴", "培训", "辅导", "幼儿园", "健身房", "理发店"
    )

    /** 名字里带这些，多半是有年头的实体。刻意收窄：路、街、站、广场这类词太宽，别放进来 */
    private val NAME_HINTS = listOf(
        "旧址", "故居", "纪念馆", "纪念", "会址", "遗址", "遗迹", "文物", "陵园", "墓",
        "里", "坊", "邨", "村", "巷", "堂", "庙", "寺", "观", "宫", "塔", "庵",
        "厂", "纱厂", "机厂", "电厂", "水厂", "船厂", "铁厂", "药厂", "印染",
        "码头", "仓库", "仓栈", "货栈", "渡口", "邮局", "海关",
        "桥", "堤", "闸", "泵", "水塔", "烟囱", "碉堡",
        "大楼", "大厦", "饭店", "旅馆", "客栈", "剧院", "影院", "电影院", "戏院", "书场",
        "学校", "大学", "中学", "小学", "书院", "图书馆", "医院", "教堂", "公园", "花园",
        "银行", "洋行", "商会", "工会", "报社", "印书局", "菜场", "厂区", "总厂"
    )

    /** 严格模式只用这一档：必须是明确的老建筑、老机构，泛泛的公园、花园、村不算 */
    private val STRICT_HINTS = listOf(
        "旧址", "故居", "遗址", "遗迹", "纪念馆", "会址", "文物", "陵园", "墓",
        "厂", "纱厂", "机厂", "电厂", "水厂", "船厂", "铁厂", "药厂", "印染",
        "码头", "仓库", "仓栈", "货栈", "渡口", "邮局", "海关", "教堂",
        "庙", "寺", "塔", "堂", "大楼", "洋行", "银行", "里", "坊", "巷",
        "桥", "堤", "闸", "水塔", "烟囱", "碉堡", "剧院", "影院", "戏院",
        "饭店", "旅馆", "客栈", "书院", "印书局"
    )

    /**
     * 「来伊份(汾西路三店)」这种门店名，括号里写着分店，直接判负。
     * 字符组只收真正的零售后缀：早先还收着「馆」「部」，把「上海博物馆(东馆)」
     * 「上海图书馆(东馆)」这类正经场馆一起误杀了。
     */
    private val STORE_NAME = Regex("[（(][^）)]{0,20}(店|铺|专柜|门市)[）)]")

    /** 新建楼盘与商业体的通用尾词：高德常把它们标成「风景名胜」，但它们没有历史 */
    private val REAL_ESTATE = listOf(
        "商业街", "步行街", "天地", "丽景", "华庭", "名邸", "壹号", "华府",
        "国际社区", "公馆", "售楼", "示范区", "体验中心", "城市广场", "生活广场"
    )

    /** 类型白名单：只留真正有公共记忆的两类。地名地址信息多是路口与门牌，不算 */
    private val CATEGORY_ALLOW = setOf("风景名胜", "科教文化服务")

    /** 公共机构类型：「医院学校机关」开关关掉时整类封杀 */
    private val PUBLIC_INSTITUTION = setOf("医疗保健服务", "科教文化服务", "政府机构及社会团体")

    /** 打开「公司门店也算」时额外放行的类型 */
    private val BUSINESS_CATEGORY = setOf("公司企业", "商务住宅", "购物服务")

    /** 类型黑名单：商业与居住 */
    private val CATEGORY_BLOCK = setOf(
        "购物服务", "餐饮服务", "生活服务", "医疗保健服务", "汽车服务", "摩托车服务",
        "体育休闲服务", "金融保险服务", "公司企业", "住宿服务", "商务住宅"
    )

    fun isWorthTelling(poi: PoiInfo, policy: GatePolicy = GatePolicy()): Boolean {
        val name = poi.name.trim()
        if (name.isEmpty()) return false
        // 真地名很少超过十八字；长串多半是「XX街道XX路88弄XX服务站」这类机构全称
        if (name.length > 18) return false
        if (STORE_NAME.containsMatchIn(name)) return false

        // 1. 用户自己写下的关键词，优先级最高：他比任何规则都清楚自己想看什么
        if (policy.keywords.any { name.contains(it) }) return true

        // 2. 日常消费与办事机构，怎么设置都拒。
        //    必须排在严格模式之前，否则「静安寺地铁站」会被 STRICT_HINTS 的「寺」放行
        if (BLOCK_ALWAYS.any { name.contains(it) }) return false

        // 3. 严格模式：只认老建筑，宁可漏
        if (policy.strictOnly) return STRICT_HINTS.any { name.contains(it) }

        val category = categoryOf(poi.type)

        // 4. 商业主体：默认拒，用户明确要求时放行
        if (!policy.allowBusiness && BLOCK_BUSINESS.any { name.contains(it) }) return false

        // 5. 医院、学校、机关整类开关
        if (!policy.allowPublicInstitution && category in PUBLIC_INSTITUTION) return false

        // 新建楼盘的配套商业街常叫「XX公园·XX街」「XX华庭」，名字里带「公园」「大楼」不算数
        if (REAL_ESTATE.any { name.contains(it) }) return false

        if (NAME_HINTS.any { name.contains(it) }) return true

        if (category in CATEGORY_BLOCK) {
            return policy.allowBusiness && category in BUSINESS_CATEGORY
        }
        return category in CATEGORY_ALLOW
    }

    /** 「购物服务;专卖店;自行车专卖店」取第一段中文分类 */
    private fun categoryOf(type: String): String = type.substringBefore(';').trim()

    /**
     * 同一栋楼里往往挂着好几个 POI：一栋楼既是医院，也是某某公司注册地。
     * 只按距离排会随机命中，所以再给一个显著性权重：越小越该被当作「这里是哪儿」。
     */
    fun priority(poi: PoiInfo): Int {
        val name = poi.name
        val category = categoryOf(poi.type)
        return when {
            listOf("旧址", "故居", "遗址", "纪念馆", "会址", "遗迹").any { name.contains(it) } -> 0
            category == "风景名胜" -> 1
            name.contains("邮局") || name.contains("海关") || name.contains("码头") ||
                name.contains("仓库") || name.contains("水厂") || name.contains("船厂") -> 1
            category == "科教文化服务" -> 2
            name.contains("医院") || name.contains("卫生院") || name.contains("防治所") -> 2
            category == "政府机构及社会团体" -> 3
            else -> 4
        }
    }

    /** 高德的分类串（购物服务;专卖店;自行车专卖店）对用户没意义，翻成人话 */
    fun friendlyType(type: String): String {
        val en = com.shanghai.memory.ui.theme.ThemeState.isEnglish
        return when (categoryOf(type)) {
            "购物服务" -> if (en) "Shop" else "店铺"
            "餐饮服务" -> if (en) "Dining" else "餐饮"
            "生活服务" -> if (en) "Services" else "生活服务"
            "医疗保健服务" -> if (en) "Medical" else "医疗"
            "汽车服务", "摩托车服务" -> if (en) "Auto Shop" else "车行"
            "体育休闲服务" -> if (en) "Recreation" else "休闲场所"
            "金融保险服务" -> if (en) "Finance" else "金融网点"
            "公司企业" -> if (en) "Office" else "单位"
            "住宿服务" -> if (en) "Lodging" else "住宿"
            "商务住宅" -> if (en) "Residential" else "住宅区"
            "风景名胜" -> if (en) "Landmark" else "景点"
            "科教文化服务" -> if (en) "Cultural Site" else "文化场所"
            "交通设施服务" -> if (en) "Transit" else "交通设施"
            "地名地址信息" -> if (en) "Place" else "地点"
            "政府机构及社会团体" -> if (en) "Civic" else "机关"
            "公共设施" -> if (en) "Public Facility" else "公共设施"
            "" -> ""
            else -> categoryOf(type)
        }
    }

    /**
     * 给界面用的解释：为什么这里没得讲。
     * 只说「这里没有可讲述的地标」，用户只会觉得软件坏了——必须告诉他拦在哪一条上。
     * 分支顺序要和 [isWorthTelling] 的判定顺序对齐，否则理由会说错一条。
     */
    fun refusalReason(poi: PoiInfo, policy: GatePolicy = GatePolicy()): String {
        val en = com.shanghai.memory.ui.theme.ThemeState.isEnglish
        val name = poi.name
        val category = categoryOf(poi.type)
        val label = friendlyType(poi.type).ifBlank { if (en) "place" else "场所" }
        if (en) {
            return when {
                name.length > 18 -> "\"${name.take(16)}…\" looks like a full organization title rather than a landmark"
                STORE_NAME.containsMatchIn(name) -> "\"$name\" is a retail branch without shared urban history"
                BLOCK_ALWAYS.any { name.contains(it) } -> "\"$name\" is a daily shop, service, or transit node without public heritage"
                policy.strictOnly -> "\"$name\" didn't match the strict historic-building list"
                !policy.allowPublicInstitution && category in PUBLIC_INSTITUTION ->
                    "\"$name\" is a $label site, and civic/school/hospital places are currently filtered out"
                !policy.allowBusiness && BLOCK_BUSINESS.any { name.contains(it) } ->
                    "\"$name\" is a commercial venue—enable \"Include shops & offices\" in Settings if you'd like to hear about it"
                REAL_ESTATE.any { name.contains(it) } -> "\"$name\" is a modern commercial or residential complex"
                else -> "\"$name\" ($label) doesn't have enough public history to tell"
            }
        }
        return when {
            name.length > 18 -> "「${name.take(12)}…」更像是长串机构名，不太像一处具体地标"
            STORE_NAME.containsMatchIn(name) -> "「$name」是临街门店，咱们先跳过日常消费场所"
            BLOCK_ALWAYS.any { name.contains(it) } -> "「$name」属于日常消费、办事或交通路口，没有城市历史可讲"
            policy.strictOnly -> "「$name」不在老建筑名单里（当前开启了严格模式）"
            !policy.allowPublicInstitution && category in PUBLIC_INSTITUTION ->
                "「$name」属于$label，你在设置里关掉了「医院、学校和机关也算」"
            !policy.allowBusiness && BLOCK_BUSINESS.any { name.contains(it) } ->
                "「$name」是商业门店或公司，想听的话可以在设置里打开「普通商铺、写字楼也算」"
            REAL_ESTATE.any { name.contains(it) } -> "「$name」是新建楼盘或商业体，还留不下多少岁月故事"
            else -> "「$name」是$label，暂时没有可讲的城市记忆"
        }
    }
}
