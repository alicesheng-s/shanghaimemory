package com.shanghai.memory.data

import org.json.JSONObject

/**
 * org.json 的 optString 有个坑：键存在但值是 JSON null 时，它返回的是字符串 "null"，
 * 而不是空串。服务商偶尔会吐 {"content": null, "reasoning_content": null}，
 * 于是聊天正文里就凭空多出一个 "null"，思考过程也会被当成有内容。
 *
 * 统一从这里读：值缺失或为 null 都按「没有」处理。
 */
internal fun JSONObject.text(key: String, fallback: String = ""): String {
    if (isNull(key)) return fallback
    return optString(key, fallback)
}
