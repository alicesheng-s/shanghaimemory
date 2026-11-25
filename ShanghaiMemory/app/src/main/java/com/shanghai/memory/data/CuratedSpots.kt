package com.shanghai.memory.data

/**
 * 预设主题的候选地点库。
 *
 * 这些点都是上海确实存在、且能在地图上检索到的工业与电力遗存。
 * 只存「名字 + 检索词 + 区域 + 一句话」，坐标不写死 —— 运行时用高德按名字反查，
 * 一来避免手抄坐标出偏差，二来用户看到的名字和地图上的图钉一定是同一个。
 */
data class CuratedSpot(
    val name: String,
    val area: String,
    val blurb: String,
    /** 高德检索用的关键词，默认与 name 相同；行政区重名时加限定词 */
    val searchKey: String = name,
)

data class SpotTheme(
    val key: String,
    val title: String,
    val subtitle: String,
    val spots: List<CuratedSpot>,
)

object CuratedSpots {

    /** 电力之旅：从第一盏电灯到远东最大的烟囱 */
    private val POWER = listOf(
        CuratedSpot(
            name = "杨树浦电厂遗迹公园",
            area = "杨浦滨江 · 杨树浦路2800号",
            blurb = "前身是 1913 年建成的远东第一火力发电厂，105 米烟囱曾是船只进黄浦江时看见的第一件东西。2015 年关停，如今是江边的遗址公园。"
        ),
        CuratedSpot(
            name = "杨树浦水厂",
            area = "杨浦 · 杨树浦路830号",
            blurb = "1883 年 8 月 1 日正式通水，中国第一座近现代化水厂，英国城堡式厂房至今仍在供水，门口那条路就是它的名字。"
        ),
        CuratedSpot(
            name = "上海当代艺术博物馆",
            area = "黄浦 · 花园港路200号",
            blurb = "南市发电厂旧址，源起 1897 年华商自办的南市电灯厂。老厂房改成了美术馆，165 米烟囱至今保留，夜里亮起巨型温度计。"
        ),
        CuratedSpot(
            name = "世界技能博物馆",
            area = "杨浦滨江 · 杨树浦路1578号",
            blurb = "原永安栈房，1921 年永安纺织公司的核心仓库，双子楼曾是全国最大的仓库之一，见证民族棉纺业的起落。"
        ),
        CuratedSpot(
            name = "绿之丘",
            area = "杨浦滨江 · 杨树浦路1500号",
            blurb = "烟草公司机修仓库改造，层层退台长满植物，走上去能一路通到江边，是杨浦滨江极具辨识度的一段坡道。"
        ),
        CuratedSpot(
            name = "皂梦空间",
            area = "杨浦滨江 · 杨树浦路2310号附近",
            blurb = "上海制皂厂旧址，当年「白猫」「蜂花」从这里出发。厂区改成展示空间，仍保留着一段肥皂生产线。"
        ),
        CuratedSpot(
            name = "毛麻仓库旧址",
            area = "杨浦滨江 · 杨树浦路1500号一带",
            blurb = "1920 年代的毛麻纺织仓库，清水砖墙和混凝土楼梯都还在，如今常办展览，江边这一段走起来最像老码头。"
        ),
        CuratedSpot(
            name = "上海电力大学（平凉路校区）",
            area = "杨浦 · 平凉路2103号",
            blurb = "1951 年创办的上海电业学校一路升格而来，杨浦滨江一带的电力人大多与它有关，校园里还留着老教学楼。"
        ),
    )

    /** 工业之旅：从洋务局的船坞到苏州河的纱厂 */
    private val INDUSTRY = listOf(
        CuratedSpot(
            name = "江南造船厂旧址",
            area = "黄浦 · 鲁班路一带（江南造船大厦旁）",
            blurb = "1865 年李鸿章创办的江南机器制造总局，中国第一艘自造蒸汽兵轮出自这里，2010 年前后迁往长兴岛后老厂区融入世博滨江。",
            searchKey = "江南造船厂旧址"
        ),
        CuratedSpot(
            name = "1933 老场坊",
            area = "虹口 · 溧阳路611号",
            blurb = "1933 年工部局宰牲场，伞形柱、旋转坡道、牛道交织成一栋迷宫，曾是远东规模最大的屠宰场，如今是展览与市集。"
        ),
        CuratedSpot(
            name = "四行仓库",
            area = "静安 · 光复路1号",
            blurb = "1937 年淞沪会战，八百壮士在金城、中南、大陆、盐业四家银行联合仓库坚守四昼夜，西墙弹孔至今保留。"
        ),
        CuratedSpot(
            name = "上海国际时尚中心",
            area = "杨浦 · 杨树浦路2866号",
            blurb = "原上海第十七棉纺织厂，1920 年代日商裕丰纱厂，锯齿形厂房连成一片，如今是秀场与买手店。"
        ),
        CuratedSpot(
            name = "民生码头八万吨筒仓",
            area = "浦东 · 民生路3号",
            blurb = "黄浦江边体量最大的筒仓群，1970 年代建成的散粮筒仓，三十个圆筒并排立着，现在是艺术展场。"
        ),
        CuratedSpot(
            name = "明华糖厂仓库旧址",
            area = "杨浦滨江 · 安浦路415号",
            blurb = "1924 年明治制糖公司在上海设的糖厂仓库，后来做过中央化工厂，清水砖墙的锯齿屋顶很好认。"
        ),
        CuratedSpot(
            name = "M50创意园",
            area = "普陀 · 莫干山路50号",
            blurb = "原上海春明粗纺厂，苏州河边最早一批改成画廊的老厂房，2000 年代艺术家陆续进驻，是上海厂房改造的起点。"
        ),
        CuratedSpot(
            name = "上海邮政总局大楼",
            area = "虹口 · 北苏州路276号",
            blurb = "1924 年建成的邮政大楼，钟楼、科林斯柱、屋顶花园都还在，二楼大厅仍是营业厅，柜台后头保留着百年前的风貌。"
        ),
    )

    val themes: List<SpotTheme> = listOf(
        SpotTheme(
            key = "power",
            title = "电力之旅",
            subtitle = "从 1882 年的电灯到 1913 年的烟囱，电线走过的路",
            spots = POWER
        ),
        SpotTheme(
            key = "industry",
            title = "工业之旅",
            subtitle = "船坞、纱厂、仓库与码头，黄浦江和苏州河上的生产现场",
            spots = INDUSTRY
        ),
    )

    fun byKey(key: String?): SpotTheme? = themes.firstOrNull { it.key == key }
}
