package com.shanghai.memory.data

import com.shanghai.memory.data.model.RoutePlan
import com.shanghai.memory.data.model.RouteStop

/**
 * MVP 预置路线。不依赖网络与 Key，保证演示现场一定能走通，
 * 同时也是 AI 自由策展失败时的对照样本。
 */
object CuratedRoutes {

    private val powerTrail = RoutePlan(
        routeName = "电力之旅：从第一盏灯到不夜城",
        theme = "电如何进入上海，谁在维持它",
        stops = listOf(
            RouteStop(
                seq = 1,
                name = "外滩 · 中山东一路",
                area = "黄浦区",
                stayMin = 30,
                reason = "上海是中国最早用电的城市之一，1882 年这里的江岸亮起第一批电弧灯。",
                storyHook = "先找到当年立灯的位置，再抬头看今天的泛光照明——同一段江岸，两次「亮起来」。"
            ),
            RouteStop(
                seq = 2,
                name = "上海电力大学（平凉路校区）",
                area = "杨浦区",
                stayMin = 20,
                reason = "1951 年上海电业学校创办于此脉络之上，为城市电力系统输送了大量一线技术人。",
                storyHook = "一所学校与一座城市的不停电之间，隔着多少届毕业生。"
            ),
            RouteStop(
                seq = 3,
                name = "杨树浦水厂",
                area = "杨浦区",
                stayMin = 30,
                reason = "1883 年建成通水的英式城堡水厂，至今仍在供水，是上海最早的现代市政设施之一。",
                storyHook = "在水厂围墙外听水声：这座城市最早被「管网化」的不是电，是水。"
            ),
            RouteStop(
                seq = 4,
                name = "杨浦滨江 · 绿之丘",
                area = "杨浦区",
                stayMin = 40,
                reason = "原烟草公司机修仓库改造而成，是滨江工业遗存转型的代表作。",
                storyHook = "同一条生产线上，从搬运烟叶到散步拍照，中间只隔了一次改造。"
            ),
            RouteStop(
                seq = 5,
                name = "上海当代艺术博物馆（南市发电厂旧址）",
                area = "黄浦区",
                stayMin = 60,
                reason = "原南市发电厂，165 米烟囱保留至今，2012 年转型为中国内地第一家公立当代艺术博物馆。",
                storyHook = "一座停止发电的电厂，如何继续显示这座城市的体温。"
            )
        ),
        transitionLogic = "外滩步行至南京东路站乘地铁 12 号线到宁国路，沿平凉路、杨树浦路南下串联杨浦滨江，最后经渡轮或地铁抵达当代艺术博物馆。",
        totalMin = 240
    )

    private val industryTrail = RoutePlan(
        routeName = "工业之旅：黄浦江与苏州河的两岸车间",
        theme = "中国近代工业在上海的起点与转身",
        stops = listOf(
            RouteStop(
                seq = 1,
                name = "江南造船厂旧址（世博滨江）",
                area = "黄浦区",
                stayMin = 40,
                reason = "1865 年李鸿章创办江南机器制造总局，中国近代第一家大型机器制造局，1868 年造出第一艘自造蒸汽兵轮「恬吉」号。",
                storyHook = "船坞空了，但尺度还在——站在坞底抬头，才明白「重工业」三个字有多重。"
            ),
            RouteStop(
                seq = 2,
                name = "上海当代艺术博物馆（南市发电厂旧址）",
                area = "黄浦区",
                stayMin = 60,
                reason = "南市地区的老电厂，烟囱与输煤栈桥构成完整的发电厂空间，2012 年转为当代艺术博物馆。",
                storyHook = "工业建筑改美术馆，保留结构与管线，等于把机器的骨架挂起来展示。"
            ),
            RouteStop(
                seq = 3,
                name = "四行仓库抗战纪念馆",
                area = "静安区",
                stayMin = 40,
                reason = "1937 年 10 月，谢晋元率部在此坚守四昼夜，西墙弹孔原样封存展示。",
                storyHook = "仓库是一种工业建筑，1937 年它被临时改成了堡垒。"
            ),
            RouteStop(
                seq = 4,
                name = "1933 老场坊",
                area = "虹口区",
                stayMin = 50,
                reason = "1933 年建成的工部局宰牲场，伞形柱、廊桥与坡道皆为当年工艺流程的形状。",
                storyHook = "牛道与猪道分设，宽度不同——建筑的每一处怪异都有生产上的理由。"
            ),
            RouteStop(
                seq = 5,
                name = "杨树浦水厂",
                area = "杨浦区",
                stayMin = 30,
                reason = "1883 年通水的英商水厂，哥特式城堡造型的工业建筑，140 余年持续运行。",
                storyHook = "把水泵房盖成城堡，是那一代人对「市政」这两个字的表达方式。"
            ),
            RouteStop(
                seq = 6,
                name = "提篮桥 · 上海犹太难民纪念馆",
                area = "虹口区",
                stayMin = 45,
                reason = "摩西会堂 1927 年建成，1938 至 1941 年间两万余名犹太难民落脚于此，与本地居民共用弄堂。",
                storyHook = "舟山路的晾衣杆与菜场，就是这段历史今天还在被使用的方式。"
            )
        ),
        transitionLogic = "世博滨江步行串联前两站，北上至苏州河畔的四行仓库，经地铁 10 号线到虹口 1933 老场坊，再沿江抵杨树浦水厂，最后回到提篮桥。",
        totalMin = 300
    )

    fun presets(): List<RoutePlan> = listOf(powerTrail, industryTrail)
}
