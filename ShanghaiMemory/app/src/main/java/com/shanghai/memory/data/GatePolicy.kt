package com.shanghai.memory.data

/**
 * 地标筛选策略。
 *
 * 0.3.3 之前哪些地方值得讲是写死在 LandmarkGate 里的，用户对结果不满意只能改代码。
 * 这里把判断依据抽成四个开关，默认值与原来一致：不动设置，行为不变。
 */
data class GatePolicy(
    /** 医院、学校、机关这类公共机构算不算可讲述的地点 */
    val allowPublicInstitution: Boolean = true,
    /** 公司、门店、写字楼算不算 */
    val allowBusiness: Boolean = false,
    /** 严格模式：只认明确的历史建筑，宁可漏也不错 */
    val strictOnly: Boolean = false,
    /** 自定义关键词，输入「洋行、纱厂」这类，名字命中就放行 */
    val extraKeywords: String = "",
    /** 关联更大范围地标讲述：默认开启，识别子地点时联动父级学校/园区/厂区一起检索与讲述 */
    val expandParentContext: Boolean = true
) {

    /** 逗号、顿号、分号、空格都能当分隔符，用户不必记格式 */
    val keywords: List<String>
        get() = extraKeywords
            .split(',', '，', '、', ';', '；', '\n', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    companion object {
        fun of(settings: AppSettings): GatePolicy = GatePolicy(
            allowPublicInstitution = settings.allowPublicInstitution,
            allowBusiness = settings.allowBusiness,
            strictOnly = settings.strictOnly,
            extraKeywords = settings.extraKeywords,
            expandParentContext = settings.expandParentContext
        )
    }
}
