package com.shanghai.memory.data

import com.shanghai.memory.data.model.RoutePlan
import com.shanghai.memory.data.model.RouteStop
import com.shanghai.memory.ui.theme.ThemeState

/**
 * MVP 预置路线（0.9.0 已剔除非电力、非工业站点，确保两条线路主题纯正）。
 */
object CuratedRoutes {

    private val powerTrailZh = RoutePlan(
        routeName = "电力之旅：从第一盏灯到远东第一电厂",
        theme = "电如何进入上海，电网与发电机又如何撑起不夜城",
        stops = listOf(
            RouteStop(
                seq = 1,
                name = "外滩第一盏电灯起源地",
                area = "黄浦区",
                stayMin = 25,
                reason = "1882 年 7 月上海电气公司在此附近建成中国第一座发电厂，点亮外滩首批 15 盏弧光灯。",
                storyHook = "站到南京东路与江西中路口：中国大地上最早的电灯，就是从这片街区亮起来的。",
                searchKey = "黄浦区南京东路江西中路"
            ),
            RouteStop(
                seq = 2,
                name = "上海电力公司大楼旧址",
                area = "黄浦区",
                stayMin = 25,
                reason = "1931 年竣工的装饰风艺术建筑，当年掌控上海绝大部分供电的调度中枢。",
                storyHook = "抬头看南京东路 181 号的立面线条：一百年前控制整座城市电流的大脑就在楼上。",
                searchKey = "黄浦区南京东路181号"
            ),
            RouteStop(
                seq = 3,
                name = "上海电力大学（平凉路校区）",
                area = "杨浦区",
                stayMin = 25,
                reason = "前身为 1951 年创办的上海电业学校，几十年来持续为电厂与电网输送一线技术人。",
                storyHook = "一所学校与一座城市的不停电之间，隔着多少届在深夜抢修和算负荷的毕业生。"
            ),
            RouteStop(
                seq = 4,
                name = "杨树浦电厂遗迹公园",
                area = "杨浦区",
                stayMin = 45,
                reason = "1913 年正式发电的远东第一火力发电厂，105 米烟囱与输煤栈桥至今震撼矗立。",
                storyHook = "站在巨型煤灰斗与烟囱下：昔日船只进黄浦江看见的第一件地标，就是它。"
            ),
            RouteStop(
                seq = 5,
                name = "上海当代艺术博物馆（南市发电厂旧址）",
                area = "黄浦区",
                stayMin = 60,
                reason = "源起 1897 年华商自办南市电灯厂，165 米烟囱至今亮着巨型温度计。",
                storyHook = "一座停止发电的民族电厂，怎样把 165 米烟囱变成测量城市体温的标尺。",
                searchKey = "上海当代艺术博物馆"
            )
        ),
        transitionLogic = "从外滩南京东路起点步行至南京东路 181 号电力大楼，乘地铁 12 号线至宁国路站逛电力大学与杨树浦电厂遗迹公园，最后沿江前往南市发电厂旧址。",
        totalMin = 220
    )

    private val industryTrailZh = RoutePlan(
        routeName = "工业之旅：黄浦江与苏州河的两岸车间",
        theme = "船坞、纱厂、制水与宰牲场——上海百年制造现场",
        stops = listOf(
            RouteStop(
                seq = 1,
                name = "江南造船厂旧址（江南造船大厦）",
                area = "黄浦区",
                stayMin = 40,
                reason = "1865 年李鸿章创办江南机器制造总局，1868 年造出中国第一艘自造蒸汽兵轮「恬吉」号。",
                storyHook = "船坞空了，但尺度还在——站在坞底抬头，才明白「重工业」三个字有多重。",
                searchKey = "江南造船大厦"
            ),
            RouteStop(
                seq = 2,
                name = "1862时尚艺术中心（上海船厂旧址）",
                area = "浦东新区",
                stayMin = 40,
                reason = "前身是 1862 年英商祥生船厂，江畔老造机车间保留了斑驳红砖墙与巨型行车。",
                storyHook = "从造出第一艘出口万吨轮的车间走进去，铁管与钢柱都还留着当年油污与铆钉。",
                searchKey = "1862时尚艺术中心"
            ),
            RouteStop(
                seq = 3,
                name = "1933 老场坊（工部局宰牲场）",
                area = "虹口区",
                stayMin = 45,
                reason = "1933 年建成的工部局宰牲场，伞形柱、廊桥与盘旋坡道皆为当年工艺流程的形状。",
                storyHook = "牛道与猪道分设，宽度不同——建筑的每一处怪异都有生产上的理由。",
                searchKey = "1933老场坊"
            ),
            RouteStop(
                seq = 4,
                name = "杨树浦水厂",
                area = "杨浦区",
                stayMin = 30,
                reason = "1883 年正式通水的英式城堡水厂，140 余年来至今仍在向城市供水。",
                storyHook = "把水泵房盖成城堡，是那一代人对「市政工业」四个字的表达方式。"
            ),
            RouteStop(
                seq = 5,
                name = "绿之丘（烟草公司机修仓库）",
                area = "杨浦区",
                stayMin = 35,
                reason = "原上海烟草公司机修仓库退台改造，把工业混凝土骨架变成通向江边的绿色坡道。",
                storyHook = "从搬运烟叶、修配烟机的老仓库走上去，视野在退台之间一层层向黄浦江打开。",
                searchKey = "绿之丘"
            ),
            RouteStop(
                seq = 6,
                name = "上海国际时尚中心（国棉十七厂）",
                area = "杨浦区",
                stayMin = 45,
                reason = "1921 年裕丰纱厂建成的锯齿形厂房群，地面嵌着的运纱铁轨至今仍在。",
                storyHook = "踩着地面当年推纱车的铁轨走进锯齿厂房，纺织轰鸣换成了秀场灯光。",
                searchKey = "上海国际时尚中心"
            )
        ),
        transitionLogic = "世博滨江江南造船旧址起步，过江游览船厂 1862 后北上虹口 1933 老场坊，再沿着杨树浦路一路向东串联水厂、绿之丘至国棉十七厂旧址。",
        totalMin = 280
    )

    private val powerTrailEn = RoutePlan(
        routeName = "Power Trail: From the First Lamp to the Far East's Powerhouse",
        theme = "How electricity entered Shanghai and built a sleepless city grid",
        stops = listOf(
            RouteStop(
                seq = 1,
                name = "Birthplace of Shanghai's First Electric Lamp",
                area = "Huangpu",
                stayMin = 25,
                reason = "Near here in July 1882, China's first power plant lit 15 arc lamps along the Bund.",
                storyHook = "Stand at East Nanjing & Middle Jiangxi Rd—where modern electric light first sparked in China.",
                searchKey = "黄浦区南京东路江西中路"
            ),
            RouteStop(
                seq = 2,
                name = "Former Shanghai Power Company Building",
                area = "Huangpu",
                stayMin = 25,
                reason = "A 1931 Art Deco landmark that served as the city's central electricity headquarters.",
                storyHook = "Look up at 181 East Nanjing Rd—a century ago this building commanded the city's current.",
                searchKey = "黄浦区南京东路181号"
            ),
            RouteStop(
                seq = 3,
                name = "Shanghai University of Electric Power (Pingliang Campus)",
                area = "Yangpu",
                stayMin = 25,
                reason = "Founded in 1951 as Shanghai Electric Power School, training frontline grid engineers.",
                storyHook = "Between a quiet campus and a city that never goes dark stand decades of grid builders.",
                searchKey = "上海电力大学（平凉路校区）"
            ),
            RouteStop(
                seq = 4,
                name = "Yangshupu Power Plant Relic Park",
                area = "Yangpu",
                stayMin = 45,
                reason = "Commissioned in 1913 as the Far East's largest thermal plant, preserving its 105m chimney.",
                storyHook = "Stand beneath the coal hoppers and towering stack that greeted ships entering Shanghai.",
                searchKey = "杨树浦电厂遗迹公园"
            ),
            RouteStop(
                seq = 5,
                name = "Power Station of Art (Former Nanshi Power Plant)",
                area = "Huangpu",
                stayMin = 60,
                reason = "Rooted in the 1897 Chinese-run Nanshi Light Works; its 165m chimney thermometer still glows.",
                storyHook = "A retired national power station that still takes Shanghai's temperature every night.",
                searchKey = "上海当代艺术博物馆"
            )
        ),
        transitionLogic = "Walk from East Nanjing Rd & Jiangxi Rd to 181 East Nanjing Rd, ride Line 12 east to Yangpu for the Electric Power campus and Yangshupu Power Plant Park, then head south to PSA.",
        totalMin = 220
    )

    private val industryTrailEn = RoutePlan(
        routeName = "Industry Trail: Workshops Along Two Rivers",
        theme = "Shipyards, cotton mills, waterworks, and abattoirs—Shanghai's production floor",
        stops = listOf(
            RouteStop(
                seq = 1,
                name = "Former Jiangnan Shipyard Site",
                area = "Huangpu",
                stayMin = 40,
                reason = "Founded in 1865 as Jiangnan Arsenal; built China's first domestic steam warship in 1868.",
                storyHook = "Stand beside the historic docks to feel the true scale of China's earliest heavy industry.",
                searchKey = "江南造船大厦"
            ),
            RouteStop(
                seq = 2,
                name = "1862 Theater (Shanghai Shipyard)",
                area = "Pudong",
                stayMin = 40,
                reason = "Former 1862 Boyd & Co. Shipyard machine shop retaining brick walls, rusted pipes, and gantry cranes.",
                storyHook = "Step inside a waterfront hall where ocean-going hulls gave way to dramatic stage lighting.",
                searchKey = "1862时尚艺术中心"
            ),
            RouteStop(
                seq = 3,
                name = "1933 Old Millfun (Municipal Abattoir)",
                area = "Hongkou",
                stayMin = 45,
                reason = "A 1933 municipal abattoir whose spiral ramps and umbrella columns follow industrial workflow.",
                storyHook = "Separate ramps for cattle and pigs—every strange angle had a practical production job.",
                searchKey = "1933老场坊"
            ),
            RouteStop(
                seq = 4,
                name = "Yangshupu Waterworks",
                area = "Yangpu",
                stayMin = 30,
                reason = "Opened in 1883 in British castle brickwork and still actively supplying tap water today.",
                storyHook = "Building a pump house like a fortress was how Victorians spelled municipal industry.",
                searchKey = "杨树浦水厂"
            ),
            RouteStop(
                seq = 5,
                name = "Green Hill (Tobacco Machine Repair Depot)",
                area = "Yangpu",
                stayMin = 35,
                reason = "Former tobacco machinery repair warehouse carved into terraced green riverside walkways.",
                storyHook = "Walk straight up the old workshop frame as the Huangpu River opens terrace by terrace.",
                searchKey = "绿之丘"
            ),
            RouteStop(
                seq = 6,
                name = "Shanghai International Fashion Center (No.17 Cotton Mill)",
                area = "Yangpu",
                stayMin = 45,
                reason = "Built in 1921 as Yuho Cotton Mill; sawtooth factory roofs and embedded cart tracks still remain.",
                storyHook = "Follow the iron spinning-cart rails embedded in the floor beneath sawtooth skylights.",
                searchKey = "上海国际时尚中心"
            )
        ),
        transitionLogic = "Start at Jiangnan Shipyard, cross to 1862 Shipyard Theater, head north to 1933 Old Millfun, then trace Yangshupu Road east through the Waterworks, Green Hill, and No.17 Cotton Mill.",
        totalMin = 280
    )

    private val lingangTrailZh = RoutePlan(
        routeName = "临港之旅：从滴水湖星海到东海门户",
        theme = "天文、航海、电力高校与围垦滩涂上生长出的未来之城",
        stops = listOf(
            RouteStop(
                seq = 1,
                name = "中国航海博物馆",
                area = "浦东新区",
                stayMin = 45,
                reason = "国家级航海主题殿堂，双白帆大厅里收藏了中国古代造船与远洋航行史。",
                storyHook = "走进双帆大厅前先抬头看：远洋船锚正迎着东海吹来的湿润海风。"
            ),
            RouteStop(
                seq = 2,
                name = "上海电力大学（临港校区）",
                area = "浦东新区",
                stayMin = 30,
                reason = "建有新能源微电网与智能电网实验平台，衔接杨浦老校区向未来电网延伸。",
                storyHook = "在靠近海风的校园里，风电、光伏与微电网正变成每天的实验课日常。",
                searchKey = "上海电力大学(临港校区)"
            ),
            RouteStop(
                seq = 3,
                name = "朵云书院·滴水湖店",
                area = "浦东新区",
                stayMin = 35,
                reason = "临湖 20 米玻璃幕墙书房，把滴水湖开阔水面装进整片城市阅读视野。",
                storyHook = "在临港圆心旁落座一会，抬头就能看见直径 2.6 公里的整片人工湖光。",
                searchKey = "朵云书院(滴水湖店)"
            ),
            RouteStop(
                seq = 4,
                name = "上海天文馆",
                area = "浦东新区",
                stayMin = 60,
                reason = "全球建筑规模最大的天文馆，无直角圆洞天窗让建筑本身化身日晷与天象仪。",
                storyHook = "穿过圆洞天窗洒在地上的光斑每小时都在移动，提醒你脚下地球正在自转。"
            ),
            RouteStop(
                seq = 5,
                name = "南汇嘴观海公园",
                area = "浦东新区",
                stayMin = 30,
                reason = "上海陆域最东南端的填海海岸，不锈钢「司南鱼」雕塑面向长江口与东海。",
                storyHook = "站上世纪塘大堤：背后是滴水湖新城，面前是上海与东海交接的第一道海平线。"
            )
        ),
        transitionLogic = "申港大道中国航海博物馆出发，串联沪城环路电力大学临港校区、滴水湖畔朵云书院与临港大道上海天文馆，傍晚抵南汇嘴观海公园收尾。",
        totalMin = 240
    )

    private val lingangTrailEn = RoutePlan(
        routeName = "Lingang Tour: From Dishui Lake to the East China Sea",
        theme = "Maritime heritage, power campuses, sponge parks, and astronomical architecture on reclaimed coast",
        stops = listOf(
            RouteStop(
                seq = 1,
                name = "China Maritime Museum",
                area = "Pudong",
                stayMin = 45,
                reason = "China's first national maritime museum with twin white-sail canopies and classic ship models.",
                storyHook = "Pause beneath the curved white sails before stepping inside a thousand years of sea charts.",
                searchKey = "中国航海博物馆"
            ),
            RouteStop(
                seq = 2,
                name = "Shanghai University of Electric Power (Lingang Campus)",
                area = "Pudong",
                stayMin = 30,
                reason = "Features microgrid and clean-energy labs bridging classic Pingliang grid engineering with coastal renewables.",
                storyHook = "Along this breezy coastal avenue, wind, solar, and smart grids move straight from lab to life.",
                searchKey = "上海电力大学(临港校区)"
            ),
            RouteStop(
                seq = 3,
                name = "Duoyun Books · Dishui Lake",
                area = "Pudong",
                stayMin = 35,
                reason = "Lakefront study wrapped in 20-meter glass windows facing the 2.6-kilometer circular Dishui Lake.",
                storyHook = "Look up from your page and the entire geometric harbor of Lingang spreads across the glass.",
                searchKey = "朵云书院(滴水湖店)"
            ),
            RouteStop(
                seq = 4,
                name = "Shanghai Astronomy Museum",
                area = "Pudong",
                stayMin = 60,
                reason = "World's largest planetarium by floor area, sculpted without straight walls as an architectural sundial.",
                storyHook = "Follow the sunlight disc gliding through the roof oculus—tracking Earth's rotation in real time.",
                searchKey = "上海天文馆"
            ),
            RouteStop(
                seq = 5,
                name = "Nanhuizui Seaview Park",
                area = "Pudong",
                stayMin = 30,
                reason = "Shanghai's southeasternmost reclaimed coastal cape anchored by the steel Sinan Fish monument.",
                storyHook = "Stand on the seawall: Dishui Lake lies behind you while the East China Sea opens straight ahead.",
                searchKey = "南汇嘴观海公园"
            )
        ),
        transitionLogic = "Begin at China Maritime Museum, pass SUEP Lingang Campus toward Dishui Lake's Duoyun Books and Shanghai Astronomy Museum, then finish at Nanhuizui Seaview Park.",
        totalMin = 240
    )

    fun presets(): List<RoutePlan> =
        if (ThemeState.isEnglish) listOf(powerTrailEn, industryTrailEn, lingangTrailEn)
        else listOf(powerTrailZh, industryTrailZh, lingangTrailZh)
}
