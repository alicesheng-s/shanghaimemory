package com.shanghai.memory.data

/**
 * 从精细子地名中提取所属的更大范围母体地标。
 *
 * 例：「上海电力大学浮雕墙小草坪」→「上海电力大学」；
 *     「杨树浦电厂遗迹公园(东北门)」→「杨树浦电厂遗迹公园」；
 *     「M50创意园6号楼」→「M50创意园」。
 *
 * 注意保护「杨树浦电厂遗迹公园」「江南造船厂旧址」「上海当代艺术博物馆（南市发电厂旧址）」等
 * 完整复合地标名不被内部词截断。
 */
object PlaceContext {

    private val TRAILING_GATE_PAREN =
        Regex("[（(][^）)]{0,14}(门|口|出口|入口|停车场|期|栋|座|区|馆区)[）)]$")

    /** 按长度倒序排列，优先命中完整母体词 */
    private val PARENT_ANCHORS = listOf(
        "文化创意产业园", "创意产业园", "文化创意园", "工业旅游景区",
        "遗迹公园", "森林公园", "雕塑公园", "湿地公园",
        "风景区", "创意园", "文创园",
        "博物馆", "纪念馆", "美术馆", "展览馆",
        "发电厂", "热电厂", "造船厂", "自来水厂", "造币厂",
        "图书馆", "体育场", "大学", "学院", "中学", "小学", "医院", "书院",
        "公园", "花园", "广场", "水厂", "电厂", "船厂", "纱厂", "园区", "厂区",
        "旧址", "遗址", "遗迹", "故居", "会址", "大厦", "大楼", "中心"
    )

    /** 若母体锚点后紧跟这些完整地标后缀，说明整体仍是一个母体名称，不要拦腰截断 */
    private val PROTECTED_TAIL = listOf(
        "遗迹公园", "遗址公园", "森林公园", "雕塑公园", "湿地公园",
        "旧址", "遗址", "遗迹", "故居", "会址",
        "博物馆", "纪念馆", "美术馆", "展览馆", "艺术中心",
        "大厦", "大楼", "总厂", "发电厂", "热电厂", "造船厂", "自来水厂", "造币厂",
        "创意园", "文创园", "产业园", "媒体园", "银行", "大学", "学院",
        "医院", "中学", "小学", "附属", "医学院", "分院", "研究院", "研究所", "设计院", "实验", "绿地"
    )

    fun broaden(rawName: String): String? {
        val original = rawName.trim()
        if (original.length < 4) return null

        // 1. 剥离尾部纯出入口/楼栋括号，如「杨树浦电厂遗迹公园(东北门)」
        val withoutGateParen = original.replace(TRAILING_GATE_PAREN, "").trim()
        if (withoutGateParen.length >= 3 && withoutGateParen != original) {
            return broaden(withoutGateParen) ?: withoutGateParen
        }

        // 2. 处理「母体·子点」分隔写法（避开纯行政区前缀）
        if (original.contains('·')) {
            val prefix = original.substringBefore('·').trim()
            if (prefix.length >= 3 && !prefix.endsWith("区")) {
                return broaden(prefix) ?: prefix
            }
        }

        // 3. 在名字内部寻找母体地标词，并校验后缀确实是附属小点而非复合地标本身
        var best: String? = null
        for (anchor in PARENT_ANCHORS) {
            var from = 0
            while (true) {
                val idx = original.indexOf(anchor, from)
                if (idx < 0) break
                val end = idx + anchor.length
                val head = original.substring(0, end).trim()
                val tail = original.substring(end).trim()
                if (head.length >= 3 && tail.length >= 2 && PROTECTED_TAIL.none { tail.contains(it) }) {
                    // 取包含更多信息的有效母体（比如「杨树浦电厂遗迹公园」优先于「杨树浦电厂」）
                    if (best == null || head.length > best.length) {
                        best = head
                    }
                }
                from = idx + 1
            }
        }
        return best?.takeIf { it != original }
    }
}
