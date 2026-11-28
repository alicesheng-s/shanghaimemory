package com.shanghai.memory.data

import com.shanghai.memory.ui.theme.ThemeState

/**
 * 离线内置的 12 处上海著名地标与策展介绍，不依赖网络即可完整查阅。
 */
data class FamousLandmark(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val areaZh: String,
    val areaEn: String,
    val blurbZh: String,
    val blurbEn: String,
    val detailZh: String,
    val detailEn: String,
    val searchKey: String,
    val fallbackLat: Double,
    val fallbackLng: Double,
) {
    fun name(en: Boolean = ThemeState.isEnglish): String = if (en) nameEn else nameZh
    fun area(en: Boolean = ThemeState.isEnglish): String = if (en) areaEn else areaZh
    fun blurb(en: Boolean = ThemeState.isEnglish): String = if (en) blurbEn else blurbZh
    fun detail(en: Boolean = ThemeState.isEnglish): String = if (en) detailEn else detailZh

    fun toCandidate(en: Boolean = ThemeState.isEnglish): SpotCandidate = SpotCandidate(
        name = name(en),
        area = area(en),
        blurb = blurb(en),
        searchKey = searchKey,
        lat = 0.0,
        lng = 0.0,
        distanceMeters = -1f,
        locateAttempted = false
    )
}

object FamousLandmarks {

    val all: List<FamousLandmark> = listOf(
        FamousLandmark(
            id = "bund",
            nameZh = "外滩",
            nameEn = "The Bund",
            areaZh = "黄浦 · 中山东一路",
            areaEn = "Huangpu · Zhongshan East 1st Rd",
            blurbZh = "百余年间沿黄浦江排开的「万国建筑博览群」，西望近代风云老楼，东瞰陆家嘴天际线。",
            blurbEn = "Historic riverside promenade of neoclassical banks and trading houses facing Lujiazui across the Huangpu River.",
            detailZh = """
外滩南起延安东路、北至苏州河口外白渡桥，全长约 1.5 公里，是上海近代城市史最具代表性的岸线。

1843 年上海开埠、1845 年英租界划定后，各国银行、洋行、报馆与俱乐部陆续沿中山东一路兴建大楼，形成风格多样的「万国建筑博览群」。海关大楼 1927 年建成并装上四面报时大钟；和平饭店北楼（原沙逊大厦）顶端的绿色铜金字塔自 1929 年起便是天际线上的醒目标志；原汇丰银行大楼穹顶壁画与花岗岩立面至今仍是近代建筑研究的经典标本。

站在滨江观景平台上，西侧是二十世纪初的石构金融街，东侧隔江便是陆家嘴现代摩天楼群——整座城市把两个时代直接并置在同一条视野里。
            """.trimIndent(),
            detailEn = """
Stretching about 1.5 km from Yan'an East Road north to Waibaidu Bridge at Suzhou Creek, the Bund is Shanghai's defining waterfront promenade.

After Shanghai opened as a treaty port in 1843, foreign banks, trading houses, and civic clubs lined Zhongshan East 1st Road over roughly eight decades. Highlights include the 1927 Customs House with its famous clock tower, the 1929 Sassoon House (now Fairmont Peace Hotel) crowned by an emerald-green copper pyramid, and the former HSBC Building with its neoclassical portico and mosaic domes.

From the riverside terrace, looking west reveals early 20th-century stone facades still in active civic and commercial use, while looking east across the Huangpu frames the contemporary skyline of Lujiazui.
            """.trimIndent(),
            searchKey = "外滩",
            fallbackLat = 31.2397,
            fallbackLng = 121.4903
        ),
        FamousLandmark(
            id = "shanghai_museum",
            nameZh = "上海博物馆",
            nameEn = "Shanghai Museum",
            areaZh = "黄浦 · 人民大道201号",
            areaEn = "Huangpu · 201 Renmin Ave",
            blurbZh = "「天圆地方」造型的中国古代艺术重镇，馆藏青铜器、陶瓷、书画与玉器尤负盛名。",
            blurbEn = "Major museum of ancient Chinese art famed for its bronzes, ceramics, calligraphy, and paintings in a 'round heaven, square earth' building.",
            detailZh = """
上海博物馆创办于 1952 年，位于人民广场的人民大道馆舍于 1996 年建成开放，外观取中国传统文化「天圆地方」之意，上圆下方、中庭通透，本身已是上海二十世纪九十年代的地标公共建筑之一。

馆藏聚焦中国古代艺术精华，尤以商周青铜器（如大克鼎）、历代陶瓷、名家书画、玺印、古代玉器与少数民族工艺品著称，展陈系统而克制，适合放慢脚步细看器物纹饰与时代脉络。

如今上海博物馆已形成人民广场馆与浦东世纪大道东馆双馆协同格局：人民广场馆继续深耕专题特展与经典馆藏，东馆则以中国古代艺术通史陈列展开。
            """.trimIndent(),
            detailEn = """
Founded in 1952, Shanghai Museum's People's Square landmark opened in 1996 with an architectural silhouette inspired by the traditional cosmology of a round heaven and square earth.

Its collections rank among the finest for ancient Chinese art, especially Shang and Zhou dynasty bronzes (including the Da Ke Ding tripod), celadon and porcelain ceramics, classical calligraphy and paintings, seals, and carved jade.

Together with Shanghai Museum East in Pudong, the People's Square venue anchors special international exhibitions alongside signature treasures of Chinese art history.
            """.trimIndent(),
            searchKey = "上海博物馆",
            fallbackLat = 31.2286,
            fallbackLng = 121.4754
        ),
        FamousLandmark(
            id = "disney",
            nameZh = "上海迪士尼度假区",
            nameEn = "Shanghai Disney Resort",
            areaZh = "浦东 · 川沙新镇黄赵路310号",
            areaEn = "Pudong · 310 Huangzhao Rd, Chuansha",
            blurbZh = "中国内地首座迪士尼度假区，将经典童话主题园区、奇幻童话城堡与中国园林意象融为一体。",
            blurbEn = "Mainland China's first Disney resort, home to the Enchanted Storybook Castle, Zootopia, and themed lands blending classic stories with Chinese elements.",
            detailZh = """
上海迪士尼度假区位于浦东新区川沙新镇，2016 年 6 月 16 日正式开园，是中国内地第一座、也是全球第六座迪士尼度假区。

园内的核心地标「奇幻童话城堡」汇聚了所有迪士尼公主主题意象；宝藏湾以海盗题材大型实景演出与水下投影乘骑闻名；「疯狂动物城」作为全球首个该主题园区也已在这里落地，高度还原电影里的街区尺度与细节。米奇大街与奇想花园则在建筑装饰与十二生肖壁画等细节中融入了海派与东方文化元素。

度假区还包括迪士尼小镇、星愿公园与主题酒店，整体园区开阔，适合留足一整天慢慢游览与观看夜间烟花幻影秀。
            """.trimIndent(),
            detailEn = """
Opened in June 2016 in Pudong's Chuansha New Town, Shanghai Disney Resort is mainland China's first Disney theme park destination.

At its heart stands the Enchanted Storybook Castle—the tallest Disney castle in the world—surrounded by lands including Treasure Cove, Tomorrowland, Fantasyland, and the world's first Zootopia-themed land. Gardens of Imagination weaves Chinese zodiac murals and traditional landscape motifs into Mickey Avenue's waterfront plaza.

Complemented by Disneytown and Wishing Star Park, it is designed as a full-day cultural and entertainment resort.
            """.trimIndent(),
            searchKey = "上海迪士尼度假区",
            fallbackLat = 31.1413,
            fallbackLng = 121.6580
        ),
        FamousLandmark(
            id = "shanghai_library",
            nameZh = "上海图书馆",
            nameEn = "Shanghai Library",
            areaZh = "徐汇 · 淮海中路1555号",
            areaEn = "Xuhui · 1555 Middle Huaihai Rd",
            blurbZh = "承载沪上几代读者记忆的综合研究型公共图书馆，馆藏古籍、近代报刊与家谱资料极丰。",
            blurbEn = "One of China's largest public and research libraries, prized for rare classics, modern Shanghai periodicals, and genealogical archives.",
            detailZh = """
上海图书馆创立于 1952 年，淮海中路馆舍坐落于衡复历史文化风貌区边缘，1996 年启用时双塔层叠的「知识阶梯」造型成为徐汇极具辨识度的文化建筑。

除海量中外文书刊借阅外，上图还以研究级馆藏闻名——尤以古籍善本、近代上海报刊文献、老唱片及全球规模领先的中文家谱收藏著称，兼具市民阅览室与专业文献查阅功能。

浦东世纪大道旁的上海图书馆东馆于 2022 年开放，外观宛如被雕琢的太湖石立面，与淮海中路老馆分工呼应：淮海路馆侧重学术研究与传统文献服务，东馆则主打现代阅读广场、主题馆藏与艺术展览。
            """.trimIndent(),
            detailEn = """
Established in 1952, Shanghai Library's main Xuhui campus on Middle Huaihai Road opened in 1996 with twin stepped towers symbolizing a ladder of knowledge.

Beyond everyday public reading rooms, it houses extraordinary research collections—ancient woodblock editions, early 20th-century Shanghai newspapers and magazines, historic vinyl records, and one of the world's largest Chinese genealogy archives.

Paired with Shanghai Library East in Pudong, the Huaihai Road hall remains a quiet intellectual anchor at the edge of the historic former French Concession.
            """.trimIndent(),
            searchKey = "上海图书馆",
            fallbackLat = 31.2089,
            fallbackLng = 121.4450
        ),
        FamousLandmark(
            id = "astronomy_museum",
            nameZh = "上海天文馆",
            nameEn = "Shanghai Astronomy Museum",
            areaZh = "浦东 · 临港大道380号",
            areaEn = "Pudong · 380 Lingang Ave",
            blurbZh = "全球建筑规模最大的天文馆，无直角螺旋设计把建筑本身变成一具巨大的天文仪器。",
            blurbEn = "The world's largest planetarium by building area, designed without straight walls or right angles as a working architectural astronomical instrument.",
            detailZh = """
上海天文馆（上海科技馆分馆）位于浦东新区南汇新城滴水湖畔，2021 年 7 月正式向公众开放，总建筑面积约 3.8 万平方米，是目前全球建筑面积最大的天文馆。

整座建筑没有一条直线和直角，主楼围绕「三体」运行轨迹构想出圆洞天窗、倒转穹顶与球幕影院三大标志结构：阳光穿过圆洞天窗在地面划出随四季移动的光斑，使建筑本身成为一架大型日晷与天体观察仪器。

主展区分为「家园」「宇宙」「征程」三大部分，展品涵盖陨石标本、高倍光学天象仪、嫦娥探月与天问探测模型，引导观众从太阳系出发丈量深空尺度。
            """.trimIndent(),
            detailEn = """
Located beside Dishui Lake in Lingang and opened in July 2021, the Shanghai Astronomy Museum spans roughly 38,000 square meters, making it the largest planetarium museum in the world.

Crafted without a single right angle or straight-line facade, the building uses an oculus, an inverted dome, and a floating sphere to track solar motion like a colossal architectural sundial.

Inside, three core galleries—Home, Cosmos, and Odyssey—guide visitors from Earth's seasons and real meteorite specimens to deep-space exploration and Chinese lunar and Mars mission artifacts.
            """.trimIndent(),
            searchKey = "上海天文馆",
            fallbackLat = 30.9163,
            fallbackLng = 121.9182
        ),
        FamousLandmark(
            id = "yu_garden",
            nameZh = "豫园",
            nameEn = "Yu Garden",
            areaZh = "黄浦 · 福佑路168号",
            areaEn = "Huangpu · 168 Fuyou Rd",
            blurbZh = "始建于明嘉靖年间的江南古典私家园林，「玉玲珑」太湖石与九曲桥尽显老城厢风雅。",
            blurbEn = "Classical Ming-dynasty Jiangnan garden in the old walled city, celebrated for its Exquisite Jade Rock, dragon walls, and Zigzag Bridge.",
            detailZh = """
豫园坐落于上海老城厢核心地带，始建于明嘉靖三十八年（1559 年），由四川布政使潘允端耗时近二十年为奉养父亲而筑，「豫」即愉悦安泰之意。

这座面积不大却布局精巧的江南古典园林以「奇、秀、幽」著称：园内点春堂、玉华堂、得月楼与蜿蜒龙墙错落有致，其中高逾三米的太湖石名峰「玉玲珑」被誉为江南三大名石之一；清代小刀会起义期间，点春堂也曾作为起义军城北指挥部留名史册。

园外紧邻城隍庙与九曲桥荷花池，白墙黛瓦、飞檐翘角与周边的传统市井美食，共同守住了上海建城七百余年最浓郁的「老城厢」气韵。
            """.trimIndent(),
            detailEn = """
Tucked inside Shanghai's historic old walled city, Yu Garden was begun in 1559 during the Ming dynasty by Pan Yunduan as a tranquil retreat for his father.

Masterfully partitioned by undulating dragon walls and lattice windows, the garden packs pavilions, koi ponds, and rockeries into a remarkably compact space—highlighted by the porous Exquisite Jade Rock and Dianchun Hall, which later served as a command post during the 1853 Small Swords uprising.

Just outside its gates, the nine-turn Zigzag Bridge and City God Temple preserve the bustling texture of old Shanghai market life.
            """.trimIndent(),
            searchKey = "豫园",
            fallbackLat = 31.2272,
            fallbackLng = 121.4921
        ),
        FamousLandmark(
            id = "science_museum",
            nameZh = "上海科技馆",
            nameEn = "Shanghai Science & Technology Museum",
            areaZh = "浦东 · 世纪大道2000号",
            areaEn = "Pudong · 2000 Century Ave",
            blurbZh = "世纪大道东段的银球地标，集沉浸式自然生态、工程体验与科普影院于一体的城市科学客厅。",
            blurbEn = "Signature glass-sphere science center at the eastern end of Century Avenue, showcasing ecology, robotics, space exploration, and dome theaters.",
            detailZh = """
上海科技馆位于浦东新区花木行政文化中心世纪大道东端，2001 年建成开放，建筑以螺旋上升的银色斜坡屋面与中央巨大通透玻璃球体构成，寓意科学探索由地球出发不断攀升。

馆内围绕自然、人与科技展开多个大型常设展区，包括生物万象、地壳探秘、智慧之光、信息时代、机器人世界与宇航天地，配合巨幕影院、球幕影院与四维影院，是几代上海青少年校外科学启蒙的共同记忆。

提示：上海科技馆近年来分阶段进行大规模升级改造，出行前建议留意官方开放公告与展区恢复进度。
            """.trimIndent(),
            detailEn = """
Anchoring the eastern end of Century Avenue in Pudong, the Shanghai Science & Technology Museum opened in 2001 with an ascending spiral roof and an iconic glazed central sphere symbolizing humanity's upward quest for discovery.

Its exhibition halls cover rainforest biodiversity, Earth's crust, robotics, aerospace, and foundational physics alongside giant-screen and dome theaters, making it a beloved landmark for generations of families and students.

Note: Check current exhibition hall schedules before visiting, as major phased upgrades have periodically refreshed sections of the venue.
            """.trimIndent(),
            searchKey = "上海科技馆",
            fallbackLat = 31.2193,
            fallbackLng = 121.5426
        ),
        FamousLandmark(
            id = "natural_museum",
            nameZh = "上海自然博物馆",
            nameEn = "Shanghai Natural History Museum",
            areaZh = "静安 · 北京西路510号",
            areaEn = "Jing'an · 510 West Beijing Rd",
            blurbZh = "藏身静安雕塑公园内的鹦鹉螺造型建筑，以万余件动植物化石与生态复原讲述生命演化史。",
            blurbEn = "Nautilus-inspired museum in Jing'an Sculpture Park displaying fossils, dinosaur skeletons, and global biodiversity across ten thematic zones.",
            detailZh = """
上海自然博物馆（上海科技馆分馆）新馆位于静安雕塑公园内，2015 年正式开馆，前身可追溯至十九世纪后期创立的震旦博物院与亚洲文会博物院，拥有跨越百余年的标本积累。

建筑外墙借鉴鹦鹉螺壳的几何螺旋与「细胞墙」结构，将公园绿地自然引入中庭采光井。馆内设「起源之谜」「生命长河」「演化之道」等十大常设展区，拥有马门溪龙骨架、黄河古象等镇馆标本以及海量蝴蝶、矿物与生态 diorama 场景，兼顾科学严谨与极强的视觉震撼。
            """.trimIndent(),
            detailEn = """
Set inside Jing'an Sculpture Park and opened in 2015, the Shanghai Natural History Museum traces its specimen lineage back more than a century to historic Shanghai research collections.

Shaped like a coiled nautilus shell wrapped in a cellular stone lattice, its atrium opens toward sunlit park greenery while ten immersive halls—from Mystery of Origins to River of Life—present massive sauropod dinosaur skeletons, fossil treasures, and lifelike habitat dioramas.
            """.trimIndent(),
            searchKey = "上海自然博物馆",
            fallbackLat = 31.2371,
            fallbackLng = 121.4626
        ),
        FamousLandmark(
            id = "zhujiajiao",
            nameZh = "朱家角古镇",
            nameEn = "Zhujiajiao Ancient Town",
            areaZh = "青浦 · 课植园路555号",
            areaEn = "Qingpu · 555 Kezhiyuan Rd",
            blurbZh = "「上海威尼斯」水乡名镇，明代五孔放生桥与临河明清街巷勾勒最纯粹的江南水路生活。",
            blurbEn = "Canal-laced Ming and Qing water town in Qingpu, anchored by the five-arch Fangsheng Bridge and historic riverside lanes.",
            detailZh = """
朱家角古镇地处青浦淀山湖畔，早在宋元时期已形成集市，明万历年间正式建镇，因紧邻河港交错的漕运航道，自古靠米业与布业繁盛，素有「江南明珠」之称。

镇上河道纵横，三十六座古石桥将北大街、西井街与东井街连成一片。建于明隆庆五年、清嘉庆年间重修的五孔石拱「放生桥」是上海地区现存最大最长的古石拱桥；北大街保留着「一线天」式的明清商铺风貌，课植园、大清邮局旧址等景点至今可触可感。

摇橹船穿桥而过时，青石板、乌篷船影与岸边阿婆茶、扎肉的烟火气，构成离中心城区最近的古典水乡画卷。
            """.trimIndent(),
            detailEn = """
Resting beside Dianshan Lake in Qingpu, Zhujiajiao grew from Song and Yuan river markets into a prosperous Ming-era grain and textile water town.

Dozens of stone bridges cross its calm canals, led by the five-arch Fangsheng Bridge—originally built in 1571 and rebuilt in 1812—as Shanghai's largest surviving ancient stone arch bridge. Strolling North Street reveals narrow Ming and Qing shopfronts, the restored Imperial Post Office, and Kezhi Garden's lakeside pavilions.
            """.trimIndent(),
            searchKey = "朱家角古镇",
            fallbackLat = 31.1103,
            fallbackLng = 121.0537
        ),
        FamousLandmark(
            id = "fengjing",
            nameZh = "枫泾古镇",
            nameEn = "Fengjing Ancient Town",
            areaZh = "金山 · 亭枫公路8588弄",
            areaEn = "Jinshan · Lane 8588 Tingfeng Hwy",
            blurbZh = "沪浙交界千年水乡，「三步两座桥，一望十条巷」的古镇格局与金山农民画艺术发源地。",
            blurbEn = "Millennium-old water town straddling the Shanghai–Zhejiang border, famed for dense canal bridges, covered walkways, and Jinshan folk painting.",
            detailZh = """
枫泾古镇位于上海西南门户金山区，历史上曾分属吴越交界（南北以界河相望），成镇逾千年，是中国历史文化名镇之一。

镇区水网密布，「三步两座桥，一望十条巷」是其典型写照，沿河保留有江南少见的古长廊棚，晴可遮阳、雨可避湿。从古镇走出的文化名人包括清代状元蔡以台、近代漫画先驱丁悚与丁聪父子、棋坛名宿顾水如等；当地出产的枫泾丁蹄、状元糕与黄酒声名远播，邻近的中国农民画村也彰显了极具乡土生命力的金山民间艺术。
            """.trimIndent(),
            detailEn = """
Positioned at Shanghai's southwestern gateway in Jinshan District, Fengjing spans more than a thousand years of history along an ancient border waterway between the historic Wu and Yue cultural spheres.

Known for its dense web of bridges, riverside roofed arcades, and quiet stone alleys, Fengjing nurtured notable figures from Qing scholars to modern cartoonist Ding Cong and stands close to Jinshan's celebrated folk-painting village.
            """.trimIndent(),
            searchKey = "枫泾古镇",
            fallbackLat = 30.8920,
            fallbackLng = 121.0124
        ),
        FamousLandmark(
            id = "wild_animal_park",
            nameZh = "上海野生动物园",
            nameEn = "Shanghai Wild Animal Park",
            areaZh = "浦东 · 南六公路178号",
            areaEn = "Pudong · 178 Nanliu Hwy",
            blurbZh = "中国首座国家级野生动物园，分为步行观览区与乘车猛兽散养区，可近距离观察两百余种珍稀动物。",
            blurbEn = "China's first national-level wildlife park, pairing walk-through habitats with drive-in safari zones housing over 200 species.",
            detailZh = """
上海野生动物园位于浦东新区宣桥镇，1995 年建成开放，是中国首座由政府与行业共建的国家级野生动物园，占地约 153 公顷，汇集世界各地两百余种、上万头（只）珍稀野生动物。

园区结构鲜明地分为「步行区」和「车入区」两大部分：步行区可看到大熊猫、金丝猴、火烈鸟、企鹅与长颈鹿等动物在贴近自然生境的馆舍中活动；车入区则模拟草原与荒野格局，乘观光投喂车穿过食草动物漫步区与东北虎、非洲狮、棕熊等猛兽领地，体验与传统城市动物园截然不同的空间尺度。
            """.trimIndent(),
            detailEn = """
Opened in 1995 in Pudong's Xuanqiao Town, the Shanghai Wild Animal Park was China's first national-level safari park, covering roughly 153 hectares and caring for over 200 wildlife species.

Its layout splits into a spacious walk-through park—home to giant pandas, golden snub-nosed monkeys, giraffes, and flamingos—and a vehicle safari zone where visitors ride through open-range grasslands and predator habitats for lions, Siberian tigers, and bears.
            """.trimIndent(),
            searchKey = "上海野生动物园",
            fallbackLat = 31.0558,
            fallbackLng = 121.7209
        ),
        FamousLandmark(
            id = "haichang",
            nameZh = "海昌海洋公园",
            nameEn = "Shanghai Haichang Ocean Park",
            areaZh = "浦东 · 银飞路166号",
            areaEn = "Pudong · 166 Yinfei Rd",
            blurbZh = "紧邻滴水湖的海洋主题乐园，涵盖极地科普展示馆、大型水下隧道、虎鲸科普剧场与亲子游乐设施。",
            blurbEn = "Ocean-themed park near Dishui Lake featuring polar wildlife pavilions, deep-sea tunnels, marine theater shows, and family attractions.",
            detailZh = """
上海海昌海洋公园坐落于浦东临港滴水湖畔，2018 年正式开园，围绕海洋生态文化与极地科普体验打造五大主题区（人鱼海湾、极地小镇、冰雪王国、海底奇域与海洋部落）。

园区设南极企鹅馆、海兽探秘馆、冰山北极馆、海底世界馆、火山鲸鲨馆及珊瑚水母馆等大型展示场馆，配以巨型亚克力视窗与水下观光隧道，可以近距离观察企鹅、北极熊、白鲸、鲸鲨与各色远洋鱼群。园区同时设有奥特曼主题馆、时空缆车与海洋花车巡游，适合结合附近的上海天文馆与滴水湖安排临港周末线路。
            """.trimIndent(),
            detailEn = """
Situated near Dishui Lake in Pudong's Lingang area and opened in 2018, Shanghai Haichang Ocean Park spans five marine zones exploring polar ecosystems and deep-sea habitats.

Highlights include the Antarctic Penguin Pavilion, Iceberg Arctic Hall, Volcano Whale Shark Aquarium, and underwater viewing tunnels showcasing belugas, polar bears, sharks, and glowing jellyfish—easily paired with the nearby Shanghai Astronomy Museum for a Lingang weekend trip.
            """.trimIndent(),
            searchKey = "上海海昌海洋公园",
            fallbackLat = 30.9136,
            fallbackLng = 121.9117
        ),
    )
}
