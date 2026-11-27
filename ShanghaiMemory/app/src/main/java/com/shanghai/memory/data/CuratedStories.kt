package com.shanghai.memory.data

import com.shanghai.memory.data.model.PoiInfo
import com.shanghai.memory.ui.theme.ThemeState

/**
 * 离线策展底稿：无 Key、无网、模型报错时的体验下限。
 *
 * 内容为人工策展，刻意只写高置信度的部分，凡有争议或未能核实的年代、
 * 人名一律标「此事待考」，与线上生成版共用同一套合规标注规则。
 */
object CuratedStories {

    private data class Entry(val keys: List<String>, val bodyZh: String, val bodyEn: String)

    private val entries: List<Entry> = listOf(

        Entry(
            listOf("外滩", "万国建筑", "中山东一路", "外滩建筑群", "Bund"),
            """
这里最反常的事实是：你脚下的这段江岸，一百多年前是上海唯一允许外国人落脚的地方。
1843 年上海开埠，1845 年英租界在外滩一带划定，此后八十年间，各国银行、洋行、总会沿中山东一路一字排开，把它堆成了「万国建筑博览群」。

1923 年落成的汇丰银行大楼把穹顶画满了马赛克；1927 年的海关大楼装上了四面钟，1928 年元旦起用威斯敏斯特钟声报时，那口钟此后打断过上海无数次清晨；1929 年的沙逊大厦（今和平饭店北楼）顶上扣着一顶绿铜金字塔，直到今天仍是外滩天际线上最容易认出的一个角。设计者多为公和洋行等外资事务所，中国营造厂则承担了其中大量施工。

今天你能看到的，是这些建筑仍在被使用——银行还是银行，海关还是海关。站在江边往西看是 1920 年代，往东看是陆家嘴。上海把两个时代放在同一条视线上，这是它最嚣张也最诚实的地方。

内容由 AI 生成，未联网核实，仅供参考。
            """.trimIndent(),
            """
Here is a striking twist: over a century ago, this very strip of mudflat was the only ground in Shanghai where foreigners were officially permitted to settle.
After Shanghai opened as a treaty port in 1843 and the British Settlement took shape here in 1845, eighty years of banks, trading houses, and clubs lined Zhongshan East 1st Road into an open-air gallery of world architecture.

Completed in 1923, the HSBC Building still hides mosaic-lined domes inside; the 1927 Customs House began ringing its Westminster Quarters bell on New Year's Day 1928, marking countless mornings across the harbor; and the 1929 Sassoon House (now Fairmont Peace Hotel) still wears its oxidized green copper pyramid. Foreign firms like Palmer & Turner drafted the facades, while Chinese contractors built much of the stone and steel.

What makes the Bund remarkable today is that these buildings never became silent monuments—banks remain banks, and the Customs House still watches the river. Look west and you face the 1920s; turn east and Lujiazui rises across the water.

Curated offline story; no web check performed. For reference only.
            """.trimIndent()
        ),

        Entry(
            listOf("杨树浦水厂", "自来水厂", "Waterworks"),
            """
这座看起来像英国城堡的建筑，是 1883 年就开始给上海供水的自来水厂，而且它今天还在供水。
英商上海自来水公司投资兴建，由英国工程师 J.W. Hart 设计，1881 年动工、1883 年建成并于同年 8 月 1 日正式通水，取水口就在旁边的黄浦江。厂房的尖券、角楼、清水砖墙都不是装饰上的任性，而是当时英国工业建筑的惯常样式——把水泵房盖成城堡，是他们表达「市政」这个词的方式。

它的意义在于切换了上海人的取水方式：此前城市用水靠挑夫从黄浦江、苏州河和「沙滤水」水船供应，此后有了管网与龙头。杨树浦路 830 号的门牌至今未变，2013 年这里被列为全国重点文物保护单位。

今天你隔着围墙仍能听见水声。一座 140 多年没停过工的水厂，比任何纪念碑都更能说明什么叫基础设施。

内容由 AI 生成，未联网核实，仅供参考。
            """.trimIndent(),
            """
This red-brick fortress that looks like a British castle has been pumping tap water into Shanghai since 1883—and it hasn't stopped.
Built by the Shanghai Waterworks Company and designed by British engineer J.W. Hart, construction began in 1881 and clean water officially flowed on August 1, 1883, drawn straight from the Huangpu River beside it. Its pointed arches, turrets, and fair-faced brickwork were not whimsy—building a pump house like a castle was how Victorian engineers expressed civic permanence.

Before it opened, households relied on water carriers and riverboats; afterward came underground mains and street hydrants. The address at 830 Yangshupu Road has never changed, and in 2013 it became a nationally protected heritage site.

Stand outside the wall today and you can still hear the pumps humming—a 140-year working landmark that defines urban infrastructure better than any monument.

Curated offline story; no web check performed. For reference only.
            """.trimIndent()
        ),

        Entry(
            listOf("当代艺术博物馆", "南市发电厂", "PSA", "发电厂", "Power Station of Art"),
            """
黄浦江边那根 165 米高的烟囱，是这片江岸最后的工业遗嘱。
南市一带的电力事业始于清末，1897 年华商创办南市电灯厂是其重要源头，具体厂址几经迁移与扩建，细部沿革此事待考。可以确认的是，这里长期是上海南市地区的主力电厂，烟囱、输煤栈桥、厂房构成了完整的发电厂空间。2010 年上海世博会，厂区被改造为「城市未来馆」；2012 年，上海当代艺术博物馆（PSA）在此开馆，成为中国内地第一家公立当代艺术博物馆。

改造时刻意保留了烟囱、发电机组基座与部分管线。那根烟囱后来被装上一支巨型「温度计」，夜里亮起时，整个浦西滨江都看得见。也就是说，一座发电厂停止发电之后，还在用另一种方式显示这座城市的体温。

今天的展厅里常常很安静，但抬头看结构，你仍能读出它原本的用途——这是一座把机器搬走、把空间留下的建筑。

内容由 AI 生成，未联网核实，仅供参考。
            """.trimIndent(),
            """
That 165-meter chimney along the Huangpu River is this riverbank's loudest industrial signature.
Electricity in Nanshi dates back to 1897 when Chinese merchants founded the Nanshi Electric Light Works; exact early site shifts remain to be verified, though this plant long served as southern Shanghai's power backbone with coal trestles and turbine halls intact. Converted into the Pavilion of Future for Expo 2010, it reopened in 2012 as the Power Station of Art (PSA), mainland China's first state-run museum of contemporary art.

Architects deliberately preserved the chimney, turbine foundations, and overhead cranes. Fitted with a giant illuminated thermometer, the chimney still glows after dark—a retired power station that keeps taking the city's temperature.

The galleries are quiet now, yet one glance up at the steel trusses tells you everything: the machines moved out, and the space stayed behind.

Curated offline story; no web check performed. For reference only.
            """.trimIndent()
        ),

        Entry(
            listOf("江南造船", "江南机器制造", "江南造船厂", "Jiangnan Shipyard"),
            """
1865 年，李鸿章在上海创办江南机器制造总局，这是中国近代第一家大型机器制造局，也是中国近代工业真正的起点之一。
它最初设于虹口，后迁至高昌庙沿江一带，从此在这段黄浦江岸扎根一百四十余年。1868 年，这里造出中国第一艘自造蒸汽兵轮「恬吉」号；此后枪炮、弹药、机床、钢材、船舶陆续从这里出厂。它不只造东西，还翻译西方技术书籍、培养第一批中国工程师——工业的种子是被它连土一起搬进来的。

2008 年起，江南造船陆续迁往长兴岛，旧址在 2010 年世博会后逐步开放。今天你走在江边，看到的船坞、塔吊、厂房骨架，都是那一段历史的实物残留。

一个制造局能活一百四十多年，靠的不是某一次成功，而是每次时代换轨时它都还在场。

内容由 AI 生成，未联网核实，仅供参考。
            """.trimIndent(),
            """
In 1865, Li Hongzhang founded the Jiangnan Arsenal in Shanghai—China's first large-scale modern machinery works and a true cradle of Chinese industry.
Moving from Hongkou to the Gaochangmiao waterfront, it anchored this curve of the Huangpu for over 140 years. In 1868 it launched China's first domestically built steam warship, the Tianji, alongside machine tools, steel, and vessels—while its translation bureau turned Western engineering manuals into Chinese for the country's earliest modern engineers.

Relocation to Changxing Island began around 2008, and after Expo 2010 the old yard opened to pedestrians. The dry docks, gantry cranes, and steel frames along the water remain tangible witnesses to that century-long chapter.

Curated offline story; no web check performed. For reference only.
            """.trimIndent()
        ),

        Entry(
            listOf("曹杨新村", "曹杨", "Caoyang"),
            """
1951 年，新中国第一个工人新村在上海西郊动工兴建，第一批住进来的一千多户，多数是劳动模范和先进生产者，所以它一度被叫作「劳模村」。
曹杨新村的规划在当时是新鲜的：成片的二三层砖木住宅、公共浴室、合作社、学校、绿地，按「邻里单位」的思路排布，让工人家庭第一次拥有了独立厨卫和阳光。它解决的其实是工业城市最实际的问题——人从哪里来、住在哪里、怎么去上班。

1990 年代起，这里经历多轮成套改造与加层，老房子长高了几层，人口结构也变了。今天走在曹杨路上，红砖坡屋顶和后来的电梯楼挤在一起，界线清楚得有点残酷。

它值得停留的理由不是「好看」，而是它是一座城市对「劳动者该怎样生活」这个问题给出过的第一个具体答案。

内容由 AI 生成，未联网核实，仅供参考。
            """.trimIndent(),
            """
In 1951, construction began in western Shanghai on Caoyang New Village—the People's Republic's very first workers' housing estate, nicknamed "Model Workers' Village" after its first thousand households.
Designed around "neighborhood unit" planning, two- and three-story brick-and-timber blocks were paired with schools, co-ops, bathhouses, and pocket parks, giving factory families sunlit rooms and private kitchens for the first time.

Since the 1990s, renovations added upper floors and modern elevators, creating a candid skyline where pitched red-tile roofs sit right beside newer towers. Caoyang matters not for grandeur, but because it was a city's first concrete answer to how working people should live.

Curated offline story; no web check performed. For reference only.
            """.trimIndent()
        ),

        Entry(
            listOf("1933", "老场坊", "宰牲场", "Old Millfun"),
            """
这栋像迷宫一样的混凝土建筑，1933 年建成时是上海公共租界工部局的宰牲场，由英国建筑师巴尔弗斯设计，具体施工与改建细节此事待考。
它的结构至今让人费解：伞形柱撑起的无梁楼盖、宽窄不一的廊桥、盘旋而上的坡道、外圆内方的平面。这些都不是为了好看——牛道与猪道分设，不同体型的牲畜走不同宽度的坡道，走向屠宰间的路线互不交叉。也就是说，它的每一处怪异，都是当年工艺流程的形状。

后来它做过药厂仓库、停产闲置，2006 年前后被改造为创意园区，也就是今天的 1933 老场坊。

今天你在里面绕圈、拍照、喝咖啡，走的其实是牲畜的动线。这种把工业遗存直接变成消费空间的改造，是上海处理老房子最典型的手法：不解释，直接用。

内容由 AI 生成，未联网核实，仅供参考。
            """.trimIndent(),
            """
This concrete labyrinth opened in 1933 as the Shanghai Municipal Council's Abattoir, designed by British architect Balfours (detailed construction records remain to be verified).
Every bizarre geometry inside—mushroom-headed columns, crisscrossing skybridges, and spiraling ramps inside a square-within-a-circle plan—existed for pure industrial workflow: cattle and pigs climbed ramps of different widths so routes never crossed.

Later used as a pharmaceutical warehouse before falling quiet, it was reborn around 2006 as the 1933 Old Millfun creative hub. Today, strolling its bridges with a coffee means literally walking the historic assembly line—proof of Shanghai's habit of reusing industrial bones as they stand.

Curated offline story; no web check performed. For reference only.
            """.trimIndent()
        ),

        Entry(
            listOf("四行仓库", "四行", "Sihang"),
            """
这面墙上的弹孔是 1937 年留下的，不是修补时做旧的。
四行仓库原是大陆、金城、盐业、中南四家银行的联合仓库，钢筋混凝土结构，墙厚楼高。1937 年 10 月 26 日至 11 月 1 日，谢晋元率部坚守于此，对外称「八百壮士」，实际人数四百余人。他们背靠苏州河，隔岸就是公共租界，战斗被对岸的市民与外国记者全程看见——这是一场刻意打给世界看的仗。

仓库西墙与北墙受损最重，战后长期保留原状。2015 年，四行仓库抗战纪念馆在西侧建成开放，那面布满弹洞与炮击缺口的墙被原样封存进建筑内部展示。

今天你站在晋元路上抬头看，会觉得那面墙太新了——因为它是被保护起来的新。真正旧的东西，是墙上那些洞。

内容由 AI 生成，未联网核实，仅供参考。
            """.trimIndent(),
            """
The shell craters across this west wall are real scars from 1937—not theatrical restoration.
Built as a reinforced-concrete joint depot for four Chinese banks, Sihang Warehouse became a fortress from October 26 to November 1, 1937, when Commander Xie Jinyuan and roughly 400 soldiers (remembered as the "Eight Hundred Heroes") held out with Suzhou Creek at their back and the International Settlement watching directly across the water.

Severely battered on its west and north faces, the facade was preserved after the war and sealed inside the Sihang Warehouse Battle Memorial when it opened in 2015. Standing on Jinyuan Road, the brickwork looks clean because it is shielded—the oldest things you are looking at are the gaps left by artillery.

Curated offline story; no web check performed. For reference only.
            """.trimIndent()
        ),

        Entry(
            listOf("武康大楼", "诺曼底公寓", "武康路", "Wukang"),
            """
这艘「船」停在路口已经一百年了。
武康大楼 1924 年建成，原名诺曼底公寓（I.S.S. Normandie Apartments），由邬达克设计，万国储蓄会投资，是上海最早的外廊式公寓之一。它占着一块楔形地块，于是被做成近乎平底船的平面，沿两条马路展开，转角处收成圆弧。这样的形状不是因为浪漫，而是因为地界只给了这么多。

它所在的武康路原名福开森路，属于当时的法租界越界筑路区域，梧桐、花园住宅、公寓沿街生长，形成上海最典型的一段街景。2010 年代架空线入地之后，转角处那面著名的「船头」才彻底露出来，从此成为全城排队拍照的位置。

今天你看到的人潮，本身就是它故事的一部分：一栋为居住设计的公寓，最终成了被观看的对象。

内容由 AI 生成，未联网核实，仅供参考。
            """.trimIndent(),
            """
This brick "ocean liner" has been moored at a six-way intersection for a full century.
Completed in 1924 as the I.S.S. Normandie Apartments and designed by László Hudec, Wukang Mansion takes its flatiron-ship silhouette not from whimsy, but from the sharp wedge of land between intersecting roads along former Route Ferguson (today's Wukang Road).

Lined with plane trees and garden villas, the street grew into one of Shanghai's defining streetscapes. After overhead utility wires were buried underground in the 2010s, its famous rounded prow finally stood clear—and crowds have gathered at the corner ever since. A building designed for quiet living has become a stage watched by the entire city.

Curated offline story; no web check performed. For reference only.
            """.trimIndent()
        ),

        Entry(
            listOf("提篮桥", "摩西会堂", "犹太难民", "Tilanqiao", "Jewish Refugees"),
            """
1938 年到 1941 年间，两万多名犹太难民从欧洲逃到上海，绝大多数落脚在虹口提篮桥一带，和本地居民共用弄堂、水井与马桶。
他们能来，是因为当时的上海不需要签证。这一条「不需要」，救了很多人。摩西会堂 1927 年建成，是这一带犹太社群最重要的宗教场所，也是今天上海犹太难民纪念馆的核心建筑。纪念馆的墙上刻着一长串名字，那是能查到的人，查不到的更多。

霍山路、舟山路、长阳路一带的弄堂里，难民开过咖啡馆、理发店、面包房，和中国人做生意、做邻居。战争结束后他们陆续离开，留下的建筑在 2010 年代被逐步整理、开放。

今天走在舟山路上，你会看到晾衣杆、菜场、老人和游客混在一起。历史没有被圈起来，它就在日常生活里继续被使用。

内容由 AI 生成，未联网核实，仅供参考。
            """.trimIndent(),
            """
Between 1938 and 1941, over 20,000 European Jewish refugees arrived in Shanghai—mostly settling here in Hongkou's Tilanqiao lanes, sharing courtyards, taps, and morning markets with local families.
They could land because Shanghai required no entry visa at the time, a single open door that saved thousands of lives. Built in 1927, the Ohel Moshe Synagogue formed the heart of the neighborhood and now anchors the Shanghai Jewish Refugees Museum, whose courtyard wall carries thousands of verified names.

Along Huoshan and Zhoushan Roads, refugees ran bakeries, barbershops, and cafes beside Chinese neighbors. Walk down Zhoushan Road today and you still see laundry poles, markets, and elders living inside the exact same brick lanes—history still woven into everyday life.

Curated offline story; no web check performed. For reference only.
            """.trimIndent()
        ),

        Entry(
            listOf("上海电力大学", "电力学院", "平凉路", "Electric Power"),
            """
这条路和这所学校的关系，得从「电」进入上海说起。
上海是中国最早用电的城市之一，1882 年外滩一带亮起第一批电弧灯，此后电厂、电车、工厂把电变成城市运转的底座。要维持这套系统，就需要人——需要会看图纸、会算负荷、会在半夜抢修的人。上海电力大学的前身正是在这样的需求里生长出来的：1951 年上海电业学校创办，此后几经更名与升格，2018 年定名为上海电力大学。具体沿革细节与年份，建议以校史馆资料为准，此事待考。

它长期与电力系统紧密相连，毕业生大量进入电厂、电网与设备制造企业。也就是说，这座学校的产出不是论文，而是让城市不熄灯的那批人。

今天你站在校门口，看见的是普通的教学楼。但它背后连着的，是一条从 1882 年一直通到此刻的线路。

内容由 AI 生成，未联网核实，仅供参考。
            """.trimIndent(),
            """
This campus on Pingliang Road sits directly along the corridor that wired modern Shanghai.
After arc lamps first flickered on the Bund in 1882, power plants, tramways, and factories turned electricity into the city's lifeblood—and keeping it alive demanded engineers who could read schematics, balance grids, and repair lines at midnight. Founded in 1951 as the Shanghai Electric Power School and renamed Shanghai University of Electric Power in 2018 (granular archive milestones to be verified with the campus museum), it grew tightly alongside East China's power network.

Standing outside its gates today, the brick teaching blocks look modest, yet they connect straight back to a current running unbroken since 1882.

Curated offline story; no web check performed. For reference only.
            """.trimIndent()
        )
    )

    fun forPoi(poi: PoiInfo, expandParent: Boolean = true): String {
        val name = poi.name
        val parent = if (expandParent) PlaceContext.broaden(name) else null
        val matched = entries.firstOrNull { entry ->
            entry.keys.any { key -> name.contains(key, ignoreCase = true) }
        } ?: parent?.let { broad ->
            entries.firstOrNull { entry ->
                entry.keys.any { key -> broad.contains(key, ignoreCase = true) }
            }
        }
        return if (ThemeState.isEnglish) {
            matched?.bodyEn ?: genericEn(poi)
        } else {
            matched?.bodyZh ?: genericZh(poi)
        }
    }

    private fun genericZh(poi: PoiInfo): String = """
你现在正站在「${poi.name}」。
${if (poi.address.isNotBlank()) "位置参考：${poi.address}。" else ""}

这里没有提前写好的离线故事底稿——我们不想对不熟悉的建筑瞎编一段历史。
上海的记忆往往藏在细节里：一栋看起来寻常的楼，从前没准是老纱厂、报馆或仓库。最朴素的读法是抬头看看门牌号、砖墙的砌法，还有门口那棵老树。

到设置里填好 API Key 并联网后，AI 会先查公开资料，再替你把这栋楼的来历、人和事细细讲清楚。在那之前，先走一走、看一看吧。

内容由 AI 生成，未联网核实，仅供参考。
    """.trimIndent()

    private fun genericEn(poi: PoiInfo): String = """
You are standing near ${poi.name}.
${if (poi.address.isNotBlank()) "Location: ${poi.address}." else ""}

We don't have a pre-written offline note for this spot—and we refuse to invent history for a building we haven't checked.
Shanghai's past often hides in small clues: an ordinary facade may once have been a cotton mill, newspaper office, or guild hall. Look up at the brickwork, old house numbers, and courtyard trees.

Once you add an API key in Settings and connect online, AI will search public archives first and tell you this building's real story. Until then, enjoy walking the street.

Curated offline note; no web check performed. For reference only.
    """.trimIndent()
}
