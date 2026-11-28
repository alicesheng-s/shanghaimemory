package com.shanghai.memory.data

import com.shanghai.memory.ui.theme.ThemeState

/**
 * 预设主题的候选地点库（0.9.0 扩充至每条线 20 处，严格对齐电力与工业主题）。
 *
 * 这些点都是上海确实存在、且能在地图上检索到的电力与工业遗存。
 * 只存「名字 + 检索词 + 区域 + 一句话」，坐标不写死 —— 运行时用高德按名字反查，
 * 一来避免手抄坐标出偏差，二来用户看到的名字和地图上的图钉一定是同一个。
 */
data class CuratedSpot(
    val name: String,
    val area: String,
    val blurb: String,
    /** 高德检索用的关键词，默认与 name 相同；英文模式或行政区重名时保留标准检索词 */
    val searchKey: String = name,
)

data class SpotTheme(
    val key: String,
    val title: String,
    val subtitle: String,
    val spots: List<CuratedSpot>,
)

object CuratedSpots {

    /** 电力之旅（20 处）：电厂、电力大楼、老变电站、电气制造与电力科研地标 */
    private val POWER_ZH = listOf(
        CuratedSpot(
            name = "杨树浦电厂遗迹公园",
            area = "杨浦滨江 · 杨树浦路2800号",
            blurb = "1913 年正式发电的远东第一火力发电厂，105 米烟囱与输煤栈桥至今留存，如今改造成滨江遗址公园。"
        ),
        CuratedSpot(
            name = "上海当代艺术博物馆（南市发电厂旧址）",
            area = "黄浦 · 苗江路678号",
            blurb = "源起 1897 年华商自办的南市电灯厂，165 米烟囱改造成巨型温度计，主厂房现为公立当代艺术馆。",
            searchKey = "上海当代艺术博物馆"
        ),
        CuratedSpot(
            name = "上海闸北发电厂旧址",
            area = "杨浦 · 军工路4000号",
            blurb = "始建于 1911 年商办闸北水电公司，中国第一家民族资本发电厂，厂区设有「民族之光」电力主题基地。",
            searchKey = "上海闸北发电厂"
        ),
        CuratedSpot(
            name = "上海电力公司大楼旧址",
            area = "黄浦 · 南京东路181号",
            blurb = "1931 年竣工的装饰风艺术大楼，原工部局电气处与美商上海电力公司总部，当年掌管全城绝大部分供电。",
            searchKey = "黄浦区南京东路181号"
        ),
        CuratedSpot(
            name = "外滩第一盏电灯起源地",
            area = "黄浦 · 南京东路江西中路口",
            blurb = "1882 年 7 月上海电气公司在此附近建成中国首座中央电站，点亮外滩至虹口码头首批 15 盏弧光灯。",
            searchKey = "黄浦区南京东路江西中路"
        ),
        CuratedSpot(
            name = "西郊变电站旧址（印象西郊展厅）",
            area = "普陀 · 武宁路600号",
            blurb = "1959 年投运的上海电网首座 220 千伏变电站，老红砖配电楼如今辟为超高压电网设备展示厅。",
            searchKey = "普陀区武宁路600号"
        ),
        CuratedSpot(
            name = "静安500千伏地下变电站",
            area = "静安 · 静安雕塑公园地下",
            blurb = "2010 年世博配套投运，中国首座城市中心全地下 500 千伏变电站，把巨型电网藏进市中心公园绿地之下。",
            searchKey = "静安雕塑公园"
        ),
        CuratedSpot(
            name = "上海电力大学（平凉路校区）",
            area = "杨浦 · 平凉路2103号",
            blurb = "前身为 1951 年创办的上海电业学校，七十余年来为华东电网与发电厂输送了大量一线电力工程师。"
        ),
        CuratedSpot(
            name = "上海电力大学（临港校区）",
            area = "浦东 · 沪城环路1851号",
            blurb = "上海电力大学主校区，建有新能源微电网与智能电网实验平台，延续「光明使者」的育人脉络。"
        ),
        CuratedSpot(
            name = "卢家湾法商电车电灯公司旧址",
            area = "黄浦 · 重庆南路275号",
            blurb = "1907 年起法商电车电灯公司的枢纽大本营，当年统筹整个法租界的供电网络与有轨电车调度。",
            searchKey = "黄浦区重庆南路275号"
        ),
        CuratedSpot(
            name = "英商上海电车公司总管理处旧址",
            area = "虹口 · 北苏州路2号",
            blurb = "立于外白渡桥北堍，曾统管公共租界有轨与无轨电车线网供电与运营调度。",
            searchKey = "虹口区北苏州路2号"
        ),
        CuratedSpot(
            name = "上海电机厂",
            area = "闵行 · 江川路555号",
            blurb = "1949 年建厂，参与研制双水内冷汽轮发电机并孕育「扁担电机」精神，是新中国发电设备制造脊梁。",
            searchKey = "上海电气集团上海电机厂"
        ),
        CuratedSpot(
            name = "上海汽轮机厂",
            area = "闵行 · 江川路333号",
            blurb = "新中国第一家电站汽轮机设计制造厂，造出国内首台 6000 千瓦汽轮机，被称为「中国汽轮机摇篮」。"
        ),
        CuratedSpot(
            name = "上海锅炉厂",
            area = "闵行 · 华宁路250号",
            blurb = "闵行老工业区「四大金刚」之一，我国最早研制大型电站锅炉的基地，支撑起超临界火电机组研发。"
        ),
        CuratedSpot(
            name = "上海电缆研究所",
            area = "杨浦 · 军工路1000号",
            blurb = "源于 1945 年中央电工器材总厂，是我国电线电缆行业国家级科研基地，见证高压输电线缆演进。"
        ),
        CuratedSpot(
            name = "上海电器科学研究所",
            area = "普陀 · 武宁路505号",
            blurb = "长期承担全国低压电器、智能化开关与电机系统检测标准研发，是上海电器装备产业的技术大脑。"
        ),
        CuratedSpot(
            name = "华东电力设计院",
            area = "普陀 · 武宁路409号",
            blurb = "成立于 1953 年，主持华东电网众多大型电厂与特高压输变电工程设计，画出城市电网的蓝图。"
        ),
        CuratedSpot(
            name = "上海电气大宁媒体园（原继电器厂）",
            area = "静安 · 万荣路949号",
            blurb = "由老牌上海继电器厂厂区改造而来，厂房结构留着当年生产电网继电保护装置的工业尺度。",
            searchKey = "静安区万荣路949号"
        ),
        CuratedSpot(
            name = "吴泾热电厂",
            area = "闵行 · 龙吴路5060号",
            blurb = "1958 年动工兴建，半个多世纪以来持续向闵行工业区与中心城区输送电力和工业蒸汽。",
            searchKey = "闵行区龙吴路5060号"
        ),
        CuratedSpot(
            name = "石洞口发电厂",
            area = "宝山 · 盛石路270号",
            blurb = "1980 年代起屹立长江口的上海北部主力电源基地，也是国内大型超超临界清洁燃煤机组示范点。",
            searchKey = "宝山区盛石路270号"
        ),
    )

    private val POWER_EN = listOf(
        CuratedSpot(
            name = "Yangshupu Power Plant Relic Park",
            area = "Yangpu Riverfront · 2800 Yangshupu Rd",
            blurb = "Commissioned in 1913 as the Far East's largest thermal plant; its 105m chimney and coal trestle anchor this riverside park.",
            searchKey = "杨树浦电厂遗迹公园"
        ),
        CuratedSpot(
            name = "Power Station of Art (Former Nanshi Power Plant)",
            area = "Huangpu · 678 Miaojiang Rd",
            blurb = "Tracing back to the 1897 Chinese-run Nanshi Electric Light Works; its 165m chimney thermometer now crowns an art museum.",
            searchKey = "上海当代艺术博物馆"
        ),
        CuratedSpot(
            name = "Former Zhabei Power Plant Site",
            area = "Yangpu · 4000 Jungong Rd",
            blurb = "Founded in 1911 as Shanghai's first Chinese-capital power plant, breaking foreign utility monopolies along the river.",
            searchKey = "上海闸北发电厂"
        ),
        CuratedSpot(
            name = "Former Shanghai Power Company Building",
            area = "Huangpu · 181 East Nanjing Rd",
            blurb = "A 1931 Art Deco headquarters that once directed electricity distribution across the International Settlement.",
            searchKey = "黄浦区南京东路181号"
        ),
        CuratedSpot(
            name = "Birthplace of Shanghai's First Electric Lamp",
            area = "Huangpu · East Nanjing Rd & Middle Jiangxi Rd",
            blurb = "Near here in July 1882, China's first power station lit 15 arc lamps along the Bund, opening the electric era.",
            searchKey = "黄浦区南京东路江西中路"
        ),
        CuratedSpot(
            name = "Former Xijiao 220kV Substation (Grid Exhibition Hall)",
            area = "Putuo · 600 Wuning Rd",
            blurb = "Put into service in 1959 as Shanghai's first 220kV substation; its red-brick hall now exhibits ultra-high-voltage grid history.",
            searchKey = "普陀区武宁路600号"
        ),
        CuratedSpot(
            name = "Jing'an 500kV Underground Substation",
            area = "Jing'an · Beneath Jing'an Sculpture Park",
            blurb = "Completed for Expo 2010 as China's first all-underground 500kV urban-core substation tucked beneath a public park.",
            searchKey = "静安雕塑公园"
        ),
        CuratedSpot(
            name = "Shanghai University of Electric Power (Pingliang Campus)",
            area = "Yangpu · 2103 Pingliang Rd",
            blurb = "Founded in 1951 as Shanghai Electric Power School, training generations of engineers who keep the grid steady.",
            searchKey = "上海电力大学（平凉路校区）"
        ),
        CuratedSpot(
            name = "Shanghai University of Electric Power (Lingang Campus)",
            area = "Pudong · 1851 Huchenghuan Rd",
            blurb = "The university's modern Lingang campus featuring smart microgrids and clean-energy engineering labs.",
            searchKey = "上海电力大学临港校区"
        ),
        CuratedSpot(
            name = "Former French Tramway & Electric Co. Depot",
            area = "Huangpu · 275 South Chongqing Rd",
            blurb = "Established in 1907 in Luwan as the power and tramway nerve center serving the former French Concession.",
            searchKey = "黄浦区重庆南路275号"
        ),
        CuratedSpot(
            name = "Former Shanghai Electric Construction Co. HQ",
            area = "Hongkou · 2 North Suzhou Rd",
            blurb = "Standing just north of Waibaidu Bridge, once coordinating power lines and tram operations across the settlement.",
            searchKey = "虹口区北苏州路2号"
        ),
        CuratedSpot(
            name = "Shanghai Electric Machinery Works",
            area = "Minhang · 555 Jiangchuan Rd",
            blurb = "Founded in 1949; pioneer of dual-water-cooled turbine generators and a cornerstone of China's power equipment industry.",
            searchKey = "上海电气集团上海电机厂"
        ),
        CuratedSpot(
            name = "Shanghai Turbine Works",
            area = "Minhang · 333 Jiangchuan Rd",
            blurb = "China's first dedicated steam turbine manufacturer, producing the country's first 6,000kW power turbine.",
            searchKey = "上海汽轮机厂"
        ),
        CuratedSpot(
            name = "Shanghai Boiler Works",
            area = "Minhang · 250 Huaning Rd",
            blurb = "One of Minhang's legendary 'Four Titans,' advancing utility boilers for high-efficiency power stations.",
            searchKey = "上海锅炉厂"
        ),
        CuratedSpot(
            name = "Shanghai Electric Cable Research Institute",
            area = "Yangpu · 1000 Jungong Rd",
            blurb = "Tracing back to 1945, China's national research hub for high-voltage cables and power transmission lines.",
            searchKey = "上海电缆研究所"
        ),
        CuratedSpot(
            name = "Shanghai Electrical Apparatus Research Institute",
            area = "Putuo · 505 Wuning Rd",
            blurb = "A historic R&D and testing center shaping standards for switchgear, smart grids, and motor systems.",
            searchKey = "上海电器科学研究所"
        ),
        CuratedSpot(
            name = "East China Electric Power Design Institute",
            area = "Putuo · 409 Wuning Rd",
            blurb = "Founded in 1953, engineering major power plants and ultra-high-voltage corridors across East China.",
            searchKey = "华东电力设计院"
        ),
        CuratedSpot(
            name = "Shanghai Electric Daning Media Park (Former Relay Factory)",
            area = "Jing'an · 949 Wanrong Rd",
            blurb = "Adaptive reuse of the historic Shanghai Relay Factory where grid protection relays were once manufactured.",
            searchKey = "静安区万荣路949号"
        ),
        CuratedSpot(
            name = "Wujing Thermal Power Plant",
            area = "Minhang · 5060 Longwu Rd",
            blurb = "Begun in 1958, supplying electricity and industrial steam to southern Shanghai's manufacturing belt for over six decades.",
            searchKey = "闵行区龙吴路5060号"
        ),
        CuratedSpot(
            name = "Shidongkou Power Plant",
            area = "Baoshan · 270 Shengshi Rd",
            blurb = "A major Yangtze estuary generation base since the 1980s, known for pioneering ultra-supercritical units.",
            searchKey = "宝山区盛石路270号"
        ),
    )

    /** 工业之旅（20 处）：船厂、纱厂、制糖/制皂/造币/面粉/钢铁与仓储等纯粹工业遗存 */
    private val INDUSTRY_ZH = listOf(
        CuratedSpot(
            name = "江南造船厂旧址（江南造船大厦）",
            area = "黄浦 · 鲁班路600号",
            blurb = "1865 年李鸿章创办江南机器制造总局，造出中国第一艘自造蒸汽兵轮，是中国近代重工业的起点。",
            searchKey = "江南造船大厦"
        ),
        CuratedSpot(
            name = "1862时尚艺术中心（上海船厂旧址）",
            area = "浦东 · 滨江大道1777号",
            blurb = "源于 1862 年英商祥生船厂，江畔老造机车间保留斑驳红砖与工业行车，变身为船坞剧场。",
            searchKey = "1862时尚艺术中心"
        ),
        CuratedSpot(
            name = "复兴岛船台公园",
            area = "杨浦 · 共青路130号",
            blurb = "依托百年造船厂遗址改造，巨型龙门塔吊与 1/20 斜率下水船台原样保留在江岸绿地里。"
        ),
        CuratedSpot(
            name = "上海国际时尚中心（国棉十七厂）",
            area = "杨浦 · 杨树浦路2866号",
            blurb = "1921 年裕丰纱厂建成的锯齿形老厂房连成一片，如今是秀场与品牌园区，嵌入地面的运纱铁轨仍在。",
            searchKey = "上海国际时尚中心"
        ),
        CuratedSpot(
            name = "M50创意园（春明粗纺厂旧址）",
            area = "普陀 · 莫干山路50号",
            blurb = "原上海春明粗纺厂（前身信和纱厂），苏州河畔最早转型为画廊与工作室的老纺织厂房群。",
            searchKey = "M50创意园"
        ),
        CuratedSpot(
            name = "上海纺织博物馆（申新九厂旧址）",
            area = "普陀 · 澳门路150号",
            blurb = "坐落于民族资本申新纺织第九厂旧址，集中呈现上海百年纺织工业与工人生活记忆。",
            searchKey = "上海纺织博物馆"
        ),
        CuratedSpot(
            name = "半岛1919文化创意园（大中华纱厂）",
            area = "宝山 · 淞兴西路258号",
            blurb = "1919 年华商创办的大中华与华丰纱厂旧址，完整保留近 30 栋不同年代的纺织厂房与老织机。",
            searchKey = "半岛1919文化创意产业园"
        ),
        CuratedSpot(
            name = "创享塔（宝成纱厂旧址）",
            area = "普陀 · 叶家宅路100号",
            blurb = "源于 1918 年宝成纱厂与 1938 年工业瞭望塔老厂房，经包豪斯风格改造成为苏州河畔创意地标。",
            searchKey = "创享塔"
        ),
        CuratedSpot(
            name = "世界技能博物馆（永安栈房旧址）",
            area = "杨浦滨江 · 杨树浦路1578号",
            blurb = "原 1921 年永安纺织公司核心双子仓库，曾是全国规模最大的棉纺仓储建筑之一。",
            searchKey = "世界技能博物馆"
        ),
        CuratedSpot(
            name = "毛麻仓库旧址",
            area = "杨浦滨江 · 杨树浦路1500号一带",
            blurb = "1920 年代的毛麻纺织仓库，无梁楼盖、清水砖墙与混凝土楼梯清晰记录了百年工业建筑工艺。"
        ),
        CuratedSpot(
            name = "明华糖厂仓库旧址",
            area = "杨浦滨江 · 安浦路415号",
            blurb = "1924 年明治制糖公司在沪设立的糖厂仓库，后来做过中央化工厂，红砖锯齿屋顶极有辨识度。"
        ),
        CuratedSpot(
            name = "民生码头八万吨筒仓",
            area = "浦东 · 民生路3号",
            blurb = "1970 年代建成的黄浦江畔最大散粮筒仓群，三十座巨型圆筒并排矗立，现为工业艺术展场。"
        ),
        CuratedSpot(
            name = "杨树浦水厂",
            area = "杨浦 · 杨树浦路830号",
            blurb = "1883 年 8 月 1 日正式通水，中国第一座近现代化自来水厂，英国城堡式工业泵房至今仍在供水。"
        ),
        CuratedSpot(
            name = "绿之丘（烟草公司机修仓库）",
            area = "杨浦滨江 · 杨树浦路1500号",
            blurb = "原上海烟草公司机修仓库逐层退台改造而成，既保留了钢筋混凝土工业骨架，又成为垂直生态坡道。",
            searchKey = "绿之丘"
        ),
        CuratedSpot(
            name = "皂梦空间（上海制皂厂旧址）",
            area = "杨浦滨江 · 杨树浦路2310号附近",
            blurb = "原上海制皂厂旧址，国民品牌「白猫」「蜂花」的诞生地，展区内至今留存一段真实肥皂生产线。",
            searchKey = "杨浦区杨树浦路2310号"
        ),
        CuratedSpot(
            name = "祥泰木行旧址",
            area = "杨浦滨江 · 杨树浦路2086号",
            blurb = "始于 1902 年的英商祥泰木行木材加工厂旧址，镂空木构外立面留存着百年木材工业印记。",
            searchKey = "杨浦区杨树浦路2086号"
        ),
        CuratedSpot(
            name = "油酷艺术公园（亚细亚火油库）",
            area = "杨浦滨江 · 杨树浦路3024号",
            blurb = "前身为 1930 年代亚细亚火油公司油库，斑驳巨型储油罐、阀门与输油管线在江畔原貌保留。",
            searchKey = "油酷艺术公园"
        ),
        CuratedSpot(
            name = "1933 老场坊（工部局宰牲场）",
            area = "虹口 · 溧阳路611号",
            blurb = "1933 年工部局宰牲场，伞形柱、牛道与盘旋坡道全为屠宰工业动线所设，堪称混凝土迷宫。",
            searchKey = "1933老场坊"
        ),
        CuratedSpot(
            name = "上海造币博物馆（中央造币厂旧址）",
            area = "普陀 · 光复西路17号",
            blurb = "1933 年投产的中央造币厂旧址，首批国家工业遗产，完整见证近代精密金属铸造工业史。",
            searchKey = "上海造币博物馆"
        ),
        CuratedSpot(
            name = "宝钢工业旅游景区（宝武集团）",
            area = "宝山 · 宝杨路889号",
            blurb = "中国现代化钢铁重工业代表，能近距离感受全天候原料码头、高炉与 2050 热轧车间的工业震撼。",
            searchKey = "宝钢工业旅游景区"
        ),
    )

    private val INDUSTRY_EN = listOf(
        CuratedSpot(
            name = "Former Jiangnan Shipyard Site",
            area = "Huangpu · 600 Luban Rd",
            blurb = "Founded in 1865 as Jiangnan Arsenal, birthplace of China's first self-built steam warship and modern heavy industry.",
            searchKey = "江南造船大厦"
        ),
        CuratedSpot(
            name = "1862 Theater (Shanghai Shipyard)",
            area = "Pudong · 1777 Binjiang Ave",
            blurb = "Originated as the 1862 Boyd & Co. Shipyard; its weathered brick machine shop and gantry cranes now frame a riverside theater.",
            searchKey = "1862时尚艺术中心"
        ),
        CuratedSpot(
            name = "Fuxing Island Slipway Park",
            area = "Yangpu · 130 Gongqing Rd",
            blurb = "Built around a century-old shipbuilding slipway with towering gantry cranes preserved along the waterfront.",
            searchKey = "复兴岛船台公园"
        ),
        CuratedSpot(
            name = "Shanghai International Fashion Center (No.17 Cotton Mill)",
            area = "Yangpu · 2866 Yangshupu Rd",
            blurb = "Formerly the 1921 Yuho Cotton Mill; rows of sawtooth factory roofs and floor rail tracks remain intact.",
            searchKey = "上海国际时尚中心"
        ),
        CuratedSpot(
            name = "M50 Creative Park (Chunming Woolen Mill)",
            area = "Putuo · 50 Moganshan Rd",
            blurb = "Once Chunming Woolen Mill on Suzhou Creek—Shanghai's pioneering factory-to-gallery transformation.",
            searchKey = "M50创意园"
        ),
        CuratedSpot(
            name = "Shanghai Textile Museum (Shenxin No.9 Mill)",
            area = "Putuo · 150 Aomen Rd",
            blurb = "Housed at the historic Shenxin No.9 Cotton Mill, chronicling Shanghai's century-long textile industry.",
            searchKey = "上海纺织博物馆"
        ),
        CuratedSpot(
            name = "Peninsula 1919 (Great China Cotton Mill)",
            area = "Baoshan · 258 West Songxing Rd",
            blurb = "Nearly 30 original factory buildings from the 1919 Chinese-owned Great China & Huafeng textile mills.",
            searchKey = "半岛1919文化创意产业园"
        ),
        CuratedSpot(
            name = "The X Tower (Baocheng Cotton Mill)",
            area = "Putuo · 100 Yejiazhai Rd",
            blurb = "Tracing back to the 1918 Baocheng Cotton Mill and a 1938 industrial watchtower reborn in Bauhaus lines.",
            searchKey = "创享塔"
        ),
        CuratedSpot(
            name = "WorldSkills Museum (Wing On Warehouse)",
            area = "Yangpu Riverfront · 1578 Yangshupu Rd",
            blurb = "Formerly the 1921 Wing On Textile twin warehouses, once among China's largest industrial storage depots.",
            searchKey = "世界技能博物馆"
        ),
        CuratedSpot(
            name = "Wool & Linen Warehouse",
            area = "Yangpu Riverfront · near 1500 Yangshupu Rd",
            blurb = "A 1920s textile warehouse showcasing early flat-slab concrete columns and fair-faced brick masonry.",
            searchKey = "毛麻仓库旧址"
        ),
        CuratedSpot(
            name = "Minghua Sugar Refinery Warehouse",
            area = "Yangpu Riverfront · 415 Anpu Rd",
            blurb = "Built in 1924 as a sugar refinery depot before becoming a chemical plant, marked by its sawtooth brick roof.",
            searchKey = "明华糖厂仓库旧址"
        ),
        CuratedSpot(
            name = "Minsheng Wharf 80,000-Ton Silos",
            area = "Pudong · 3 Minsheng Rd",
            blurb = "Thirty towering 1970s bulk-grain cylinders lined up on the Huangpu, now hosting monumental art exhibitions.",
            searchKey = "民生码头八万吨筒仓"
        ),
        CuratedSpot(
            name = "Yangshupu Waterworks",
            area = "Yangpu · 830 Yangshupu Rd",
            blurb = "Opened on August 1, 1883 as China's first modern waterworks; its castle-style pump house still operates today.",
            searchKey = "杨树浦水厂"
        ),
        CuratedSpot(
            name = "Green Hill (Tobacco Machine Repair Depot)",
            area = "Yangpu Riverfront · 1500 Yangshupu Rd",
            blurb = "A former tobacco machinery warehouse carved into terraced green walkways over its exposed concrete frame.",
            searchKey = "绿之丘"
        ),
        CuratedSpot(
            name = "Soap Dream Space (Shanghai Soap Factory)",
            area = "Yangpu Riverfront · near 2310 Yangshupu Rd",
            blurb = "Site of the historic Shanghai Soap Factory, preserving an authentic soap-making line along the river.",
            searchKey = "杨浦区杨树浦路2310号"
        ),
        CuratedSpot(
            name = "Former China Import & Export Lumber Co.",
            area = "Yangpu Riverfront · 2086 Yangshupu Rd",
            blurb = "Dating from 1902, preserving the timber-framed silhouette of Shanghai's largest historic sawmill complex.",
            searchKey = "杨浦区杨树浦路2086号"
        ),
        CuratedSpot(
            name = "Oil Cool Art Park (Asiatic Petroleum Depot)",
            area = "Yangpu Riverfront · 3024 Yangshupu Rd",
            blurb = "Former 1930s Asiatic Petroleum storage depot keeping massive vintage fuel tanks, valves, and steel pipes intact.",
            searchKey = "油酷艺术公园"
        ),
        CuratedSpot(
            name = "1933 Old Millfun (Municipal Abattoir)",
            area = "Hongkou · 611 Liyang Rd",
            blurb = "A 1933 concrete abattoir whose mushroom pillars and split cattle ramps embody industrial process architecture.",
            searchKey = "1933老场坊"
        ),
        CuratedSpot(
            name = "Shanghai Mint Museum (Central Mint Site)",
            area = "Putuo · 17 West Guangfu Rd",
            blurb = "Opened in 1933 as China's Central Mint—a first-batch National Industrial Heritage site in classical stonework.",
            searchKey = "上海造币博物馆"
        ),
        CuratedSpot(
            name = "Baosteel Industrial Tour Area (Baowu Group)",
            area = "Baoshan · 889 Baoyang Rd",
            blurb = "A flagship of modern Chinese steelmaking featuring deep-water ore docks, blast furnaces, and hot-rolling mills.",
            searchKey = "宝钢工业旅游景区"
        ),
    )

    /** 临港之旅（20 处）：天文航海、大学城产学研、滴水湖湿地海绵城市与东海门户地标 */
    private val LINGANG_ZH = listOf(
        CuratedSpot(
            name = "上海天文馆",
            area = "临港 · 临港大道380号",
            blurb = "全球建筑规模最大的天文馆，无直角圆洞天窗与倒转穹顶把整栋建筑化作精密的太阳观测仪。"
        ),
        CuratedSpot(
            name = "中国航海博物馆",
            area = "临港 · 申港大道197号",
            blurb = "中国首家国家级航海博物馆，标志性双白帆造型内陈列福船、宝船模型与古今航海仪器。"
        ),
        CuratedSpot(
            name = "滴水湖",
            area = "临港 · 环湖西一路",
            blurb = "直径约 2.6 公里的圆形人工湖，由「一滴水落入大海」规划意象而生，是临港新片区的空间圆心。"
        ),
        CuratedSpot(
            name = "上海海昌海洋公园",
            area = "临港 · 银飞路166号",
            blurb = "临近滴水湖的大型海洋科普乐园，集南极企鹅、北极熊、鲸鲨展馆与水下隧道于一体。"
        ),
        CuratedSpot(
            name = "上海电力大学（临港校区）",
            area = "临港 · 沪城环路1851号",
            blurb = "建有新能源微电网与智能电网实验基地，将上海电力教育的百年薪火延伸至东海之滨。",
            searchKey = "上海电力大学(临港校区)"
        ),
        CuratedSpot(
            name = "上海海洋大学（临港校区）",
            area = "临港 · 沪城环路999号",
            blurb = "源起 1912 年江苏省立水产学校，明湖湿地与校史远洋科考档案讲述百年向海而兴历程。",
            searchKey = "上海海洋大学(临港校区)"
        ),
        CuratedSpot(
            name = "上海海事大学（临港校区）",
            area = "临港 · 海港大道1550号",
            blurb = "国际航运与海洋工程重镇，校园靠近洋山门户港，校内保留实习航运教学建筑与水运印记。",
            searchKey = "上海海事大学(临港校区)"
        ),
        CuratedSpot(
            name = "上海电机学院（临港校区）",
            area = "临港 · 水华路300号",
            blurb = "源于 1953 年上海电器制造学校，聚焦大型发电电机、风电装备与智能制造产教融合。",
            searchKey = "上海电机学院(临港校区)"
        ),
        CuratedSpot(
            name = "上海建桥学院（临港校区）",
            area = "临港 · 沪城环路1111号",
            blurb = "坐落于临港大学城中心地带的应用型高校，校园绿轴与校内雷锋文化展馆颇具人气。",
            searchKey = "上海建桥学院"
        ),
        CuratedSpot(
            name = "南汇嘴观海公园",
            area = "临港 · 世纪塘路东端",
            blurb = "上海市陆域最东南角，潮汐滩涂吹海风看日出，标志性不锈钢「司南鱼」雕塑指向大洋。",
            searchKey = "南汇嘴观海公园"
        ),
        CuratedSpot(
            name = "朵云书院·滴水湖店",
            area = "临港 · 环湖西一路91号港城广场",
            blurb = "20 米通透落地窗直面整片滴水湖，「低头看书、抬头看海」的临港人气城市文化客厅。",
            searchKey = "朵云书院(滴水湖店)"
        ),
        CuratedSpot(
            name = "临港科技智慧图书馆",
            area = "临港 · 海洋七路289弄",
            blurb = "专注航空航天、海洋科技与人工智能前沿典籍的沉浸式科技书馆，与园区科创人群相伴。"
        ),
        CuratedSpot(
            name = "星空之境海绵公园",
            area = "临港 · 环湖北三路",
            blurb = "上海标杆级城市海绵湿地公园，8 座星象主题桥梁与天文台式建筑串联起生态调蓄水系。"
        ),
        CuratedSpot(
            name = "春花秋色公园",
            area = "临港 · 环湖西一路南侧绿带",
            blurb = "沿二环带舒展的大型生态花海与林荫绿道，是环滴水湖骑行与慢跑的经典景观段。"
        ),
        CuratedSpot(
            name = "耀雪冰雪世界",
            area = "临港 · 沪城环路2088弄",
            blurb = "临港大型室内滑雪与冰雪综合体，把立体真雪滑道与极地建筑体验直接搬到东海湖畔。"
        ),
        CuratedSpot(
            name = "港城广场文化艺术中心",
            area = "临港 · 环湖西一路99号",
            blurb = "紧贴湖岸的现代街区与文化复合空间，定期承办临港公共艺术展、文创市集与湖畔演出。",
            searchKey = "港城广场"
        ),
        CuratedSpot(
            name = "东海大桥北堍",
            area = "临港 · 东海大桥起点",
            blurb = "全长 32.5 公里跨海长虹的陆上起点，远眺巨龙越过海面直通洋山深水自动化码头。",
            searchKey = "东海大桥"
        ),
        CuratedSpot(
            name = "上海鲜花港",
            area = "浦东 · 东海农场振东路",
            blurb = "紧邻临港北侧的现代花卉科技园，春日郁金香花海背后是数十年盐碱滩涂土壤改良的成果。"
        ),
        CuratedSpot(
            name = "临港当代美术馆",
            area = "临港 · 水芸路418号",
            blurb = "扎根临港新城核心区的公益当代艺术空间，以青年艺术、城市影像与公共设计展为主打。"
        ),
        CuratedSpot(
            name = "芦潮港公园",
            area = "临港 · 芦潮路168号",
            blurb = "紧邻百年渔港芦潮港与海防长堤，见证这片滩涂从古渔盐港一步步迈向国际深水门户。"
        ),
    )

    private val LINGANG_EN = listOf(
        CuratedSpot(
            name = "Shanghai Astronomy Museum",
            area = "Lingang · 380 Lingang Ave",
            blurb = "The world's largest planetarium by area, built with zero right angles as an architectural astronomical sundial.",
            searchKey = "上海天文馆"
        ),
        CuratedSpot(
            name = "China Maritime Museum",
            area = "Lingang · 197 Shengang Ave",
            blurb = "China's first national maritime museum, framed by twin white-sail canopies housing historic vessels and charts.",
            searchKey = "中国航海博物馆"
        ),
        CuratedSpot(
            name = "Dishui Lake",
            area = "Lingang · West Huanhu 1st Rd",
            blurb = "A 2.6-kilometer circular artificial lake inspired by a drop of water hitting the sea, anchoring Lingang's urban plan.",
            searchKey = "滴水湖"
        ),
        CuratedSpot(
            name = "Shanghai Haichang Ocean Park",
            area = "Lingang · 166 Yinfei Rd",
            blurb = "Major polar and deep-sea marine park near Dishui Lake with penguin halls, shark tunnels, and marine education.",
            searchKey = "上海海昌海洋公园"
        ),
        CuratedSpot(
            name = "Shanghai University of Electric Power (Lingang Campus)",
            area = "Lingang · 1851 Huchenghuan Rd",
            blurb = "Home to microgrid and smart-grid research labs, extending Shanghai's electric power heritage to the East China Sea coast.",
            searchKey = "上海电力大学(临港校区)"
        ),
        CuratedSpot(
            name = "Shanghai Ocean University (Lingang Campus)",
            area = "Lingang · 999 Huchenghuan Rd",
            blurb = "Tracing roots to 1912 fishery schools, preserving ocean expedition archives beside tranquil campus wetlands.",
            searchKey = "上海海洋大学(临港校区)"
        ),
        CuratedSpot(
            name = "Shanghai Maritime University (Lingang Campus)",
            area = "Lingang · 1550 Haigang Ave",
            blurb = "A cornerstone campus for international shipping and port engineering right beside Yangshan's logistics corridor.",
            searchKey = "上海海事大学(临港校区)"
        ),
        CuratedSpot(
            name = "Shanghai DianJi University (Lingang Campus)",
            area = "Lingang · 300 Shuihua Rd",
            blurb = "Founded in 1953 to train electrical machinery engineers, focusing today on wind power and smart manufacturing.",
            searchKey = "上海电机学院(临港校区)"
        ),
        CuratedSpot(
            name = "Shanghai Jian Qiao University",
            area = "Lingang · 1111 Huchenghuan Rd",
            blurb = "Applied engineering campus in Lingang's university town known for its green axes and community exhibition halls.",
            searchKey = "上海建桥学院"
        ),
        CuratedSpot(
            name = "Nanhuizui Seaview Park",
            area = "Lingang · Shijitang Rd",
            blurb = "Shanghai's southeasternmost reclaimed shoreline, marked by the steel 'Sinan Fish' sculpture facing open tidal flats.",
            searchKey = "南汇嘴观海公园"
        ),
        CuratedSpot(
            name = "Duoyun Books · Dishui Lake",
            area = "Lingang · 91 West Huanhu 1st Rd",
            blurb = "A lakefront bookstore and cultural study featuring 20-meter floor-to-ceiling windows overlooking Dishui Lake.",
            searchKey = "朵云书院(滴水湖店)"
        ),
        CuratedSpot(
            name = "Lingang Sci-Tech Smart Library",
            area = "Lingang · Lane 289 Haiyang 7th Rd",
            blurb = "An aerospace- and Frontier-tech-themed reading space built alongside Lingang's innovation laboratories.",
            searchKey = "临港科技智慧图书馆"
        ),
        CuratedSpot(
            name = "Starry Sky Sponge Park",
            area = "Lingang · North Huanhu 3rd Rd",
            blurb = "Flagship sponge-city wetland park linking eight constellation bridges, rain gardens, and astronomical pavilions.",
            searchKey = "星空之境海绵公园"
        ),
        CuratedSpot(
            name = "Chunhua Qiuse Park",
            area = "Lingang · West Huanhu 1st Rd Greenbelt",
            blurb = "Expansive second-ring greenbelt blooming across seasons, forming a favorite lakeside cycling and walking corridor.",
            searchKey = "春花秋色公园"
        ),
        CuratedSpot(
            name = "L+SNOW Indoor Skiing Theme Resort",
            area = "Lingang · Lane 2088 Huchenghuan Rd",
            blurb = "A monumental indoor snow and ice complex bringing real alpine slopes to Shanghai's eastern coast.",
            searchKey = "耀雪冰雪世界"
        ),
        CuratedSpot(
            name = "Harbor City Plaza Cultural Center",
            area = "Lingang · 99 West Huanhu 1st Rd",
            blurb = "Open lakeside civic plaza hosting public art installations, weekend markets, and coastal cultural events.",
            searchKey = "港城广场"
        ),
        CuratedSpot(
            name = "Donghai Bridge North Approach",
            area = "Lingang · Start of Donghai Bridge",
            blurb = "Mainland starting point of the 32.5-kilometer cross-sea bridge leading directly to Yangshan Deepwater Port.",
            searchKey = "东海大桥"
        ),
        CuratedSpot(
            name = "Shanghai Flower Port",
            area = "Pudong · Zhendong Rd, Donghai Farm",
            blurb = "Vibrant modern horticultural park north of Lingang built atop decades of coastal saline soil reclamation.",
            searchKey = "上海鲜花港"
        ),
        CuratedSpot(
            name = "Lingang Contemporary Art Museum",
            area = "Lingang · 418 Shuiyun Rd",
            blurb = "Public contemporary art venue in central Lingang spotlighting emerging artists, urban photography, and design.",
            searchKey = "临港当代美术馆"
        ),
        CuratedSpot(
            name = "Luchaogang Park",
            area = "Lingang · 168 Luchao Rd",
            blurb = "Quiet coastal park beside historic Luchao fishing harbor tracing its evolution into an international maritime gateway.",
            searchKey = "芦潮港公园"
        ),
    )

    val themes: List<SpotTheme>
        get() = if (ThemeState.isEnglish) {
            listOf(
                SpotTheme(
                    key = "power",
                    title = "Power Trail",
                    subtitle = "20 pure power & grid sites—from 1882 lamps and historic substations to modern powerhouses",
                    spots = POWER_EN
                ),
                SpotTheme(
                    key = "industry",
                    title = "Industry Trail",
                    subtitle = "20 industrial heritage sites—shipyards, mills, silos, mints, and steelworks along both rivers",
                    spots = INDUSTRY_EN
                ),
                SpotTheme(
                    key = "lingang",
                    title = "Lingang Tour",
                    subtitle = "20 coastal landmarks—astronomy & maritime museums, Dishui Lake, campuses, and sponge wetlands",
                    spots = LINGANG_EN
                ),
            )
        } else {
            listOf(
                SpotTheme(
                    key = "power",
                    title = "电力之旅",
                    subtitle = "20 处纯电力地标：从 1882 年第一盏电灯、老变电站到大型电厂与电机基地",
                    spots = POWER_ZH
                ),
                SpotTheme(
                    key = "industry",
                    title = "工业之旅",
                    subtitle = "20 处纯工业遗存：船厂、纱厂、筒仓、造币厂、糖厂与钢铁车间",
                    spots = INDUSTRY_ZH
                ),
                SpotTheme(
                    key = "lingang",
                    title = "临港之旅",
                    subtitle = "20 处东海门户地标：天文航海馆、滴水湖、大学城科创校园与海绵湿地公园",
                    spots = LINGANG_ZH
                ),
            )
        }

    fun byKey(key: String?): SpotTheme? = themes.firstOrNull { it.key == key }

    /** 英文模式下预设点位若未被模型带上 search_key，可反查对应的中文标准地名 */
    fun searchKeyFor(name: String): String? {
        val target = name.trim()
        if (target.isEmpty()) return null
        val match = (POWER_EN + INDUSTRY_EN + LINGANG_EN + POWER_ZH + INDUSTRY_ZH + LINGANG_ZH)
            .firstOrNull { it.name.equals(target, ignoreCase = true) }
        return match?.searchKey
    }
}
