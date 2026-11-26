package com.shanghai.memory.ui.render

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.shanghai.memory.ui.theme.PaletteColors

/**
 * 轻量 Markdown 渲染 + 容错。
 *
 * 需求原话是「ai 有时会返回无法解析的格式」，所以这里不做完整 Markdown 解析，
 * 只保证任何输入都能变成可读、有层次的文字：符号一律吃掉，该强调的地方给出颜色与字重，
 * 绝不会把 `**` `##` 这类原始符号留在屏幕上。
 *
 * 传 [colors] 时标题用强调色、引用用次要文字色、行内代码用亮强调色加等宽、
 * ==高亮== 有一层淡底；不传就退回纯加粗（设置页预览等场合用）。
 */
object MarkdownLite {

    private val heading = Regex("^#{1,6}\\s*(.+)$")
    private val bracketHeading = Regex("^(?:【(.+)】|\\[([^\\[\\]]+)])$")
    private val link = Regex("\\[(.+?)]\\((.+?)\\)")
    private val tableDivider = Regex("^\\|?[\\s:|-]{3,}\\|?$")
    private val orderedItem = Regex("^(\\d{1,2})[.)、]\\s*(.+)$")
    private val quote = Regex("^>\\s*(.*)$")
    private val divider = Regex("^([-*_=])\\1{2,}$")

    /** 行内标记一次扫完：粗体 / 高亮 / 行内代码 / 斜体 */
    private val inlineToken =
        Regex("\\*\\*([^*]+?)\\*\\*|==([^=]+?)==" + "|`([^`]+?)`" + "|\\*(?!\\s)([^*\\n]+?)\\*")

    fun toSpanned(raw: String, colors: PaletteColors? = null): CharSequence {
        if (raw.isBlank()) return ""
        val text = raw
            .replace("\r\n", "\n")
            .replace("```", "")
            .replace(link) { it.groupValues[1] }

        val out = SpannableStringBuilder()
        // 模型常在标题与正文之间连甩两三个空行，屏幕上就是一大块空白。这里最多留一行。
        var pendingBlank = false
        text.split('\n').forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || tableDivider.matches(trimmed)) {
                if (out.length > 0) pendingBlank = true
                return@forEach
            }
            if (out.length > 0) {
                out.append('\n')
                if (pendingBlank) {
                    out.append('\n')
                    pendingBlank = false
                }
            }
            when {
                divider.matches(trimmed) -> {
                    appendColored(out, "· · ·", colors?.textMuted)
                    pendingBlank = true
                }

                heading.matches(trimmed) -> {
                    appendHeading(
                        out,
                        heading.find(trimmed)?.groupValues?.get(1).orEmpty(),
                        colors
                    )
                    pendingBlank = true
                }

                bracketHeading.matches(trimmed) -> {
                    val match = bracketHeading.find(trimmed)
                    val title = match?.groupValues?.get(1)?.ifEmpty { match.groupValues[2] }.orEmpty()
                    appendHeading(out, title, colors)
                    pendingBlank = true
                }

                quote.matches(trimmed) -> {
                    out.append("▏ ")
                    val start = out.length
                    appendInline(out, quote.find(trimmed)?.groupValues?.get(1).orEmpty(), colors)
                    colors?.let {
                        out.setSpan(
                            ForegroundColorSpan(it.textMuted),
                            start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }

                orderedItem.matches(trimmed) -> {
                    val match = orderedItem.find(trimmed)
                    appendColored(out, "${match?.groupValues?.get(1)}. ", colors?.accent)
                    appendInline(out, match?.groupValues?.get(2).orEmpty(), colors)
                }

                isBullet(trimmed) -> {
                    appendColored(out, "· ", colors?.accent)
                    appendInline(out, stripBullet(trimmed), colors)
                }

                else -> appendInline(
                    out,
                    trimmed.replace('|', ' ').replace(Regex(" {2,}"), " "),
                    colors
                )
            }
        }
        return out.trimTrailing()
    }

    private fun SpannableStringBuilder.trimTrailing(): CharSequence {
        while (length > 0 && this[length - 1] == '\n') delete(length - 1, length)
        return this
    }

    private fun isBullet(line: String): Boolean =
        line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") ||
            line == "-" || line == "*"

    private fun stripBullet(line: String): String = when {
        line.length <= 2 -> ""
        else -> line.substring(2).trim()
    }

    private fun appendColored(out: SpannableStringBuilder, content: String, color: Int?) {
        val start = out.length
        out.append(content)
        if (color != null && out.length > start) {
            out.setSpan(
                ForegroundColorSpan(color), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun appendHeading(out: SpannableStringBuilder, content: String, colors: PaletteColors?) {
        if (content.isBlank()) return
        val start = out.length
        out.append(content)
        out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(
            RelativeSizeSpan(1.16f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        colors?.let {
            out.setSpan(
                ForegroundColorSpan(it.accent), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /** 扫一遍行内标记；没配上的残留星号与下划线直接吃掉，保证屏幕上不出现原文记号 */
    private fun appendInline(out: SpannableStringBuilder, content: String, colors: PaletteColors?) {
        if (content.isEmpty()) return
        var last = 0
        inlineToken.findAll(content).forEach { match ->
            if (match.range.first > last) {
                out.append(clean(content.substring(last, match.range.first)))
            }
            val g = match.groupValues
            val start = out.length
            when {
                g[1].isNotEmpty() -> {
                    out.append(g[1])
                    out.setSpan(
                        StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                g[2].isNotEmpty() -> {
                    out.append(g[2])
                    if (colors != null) {
                        out.setSpan(
                            BackgroundColorSpan(blend(colors.accent, colors.bg, 0.22f)),
                            start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        out.setSpan(
                            ForegroundColorSpan(colors.text),
                            start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    } else {
                        out.setSpan(
                            StyleSpan(Typeface.BOLD),
                            start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }

                g[3].isNotEmpty() -> {
                    out.append(g[3])
                    out.setSpan(
                        TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    colors?.let {
                        out.setSpan(
                            ForegroundColorSpan(it.accentBright),
                            start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }

                else -> {
                    out.append(g[4])
                    out.setSpan(
                        StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            last = match.range.last + 1
        }
        if (last < content.length) out.append(clean(content.substring(last)))
    }

    private fun clean(text: String): String = text
        .replace("__", "")
        .replace("**", "")
        .replace(Regex("(?<!\\*)\\*(?!\\*)"), "")

    /** 把 accent 淡淡地压到底色上，做高亮背景 */
    private fun blend(fg: Int, bg: Int, ratio: Float): Int = Color.rgb(
        (Color.red(fg) * ratio + Color.red(bg) * (1 - ratio)).toInt().coerceIn(0, 255),
        (Color.green(fg) * ratio + Color.green(bg) * (1 - ratio)).toInt().coerceIn(0, 255),
        (Color.blue(fg) * ratio + Color.blue(bg) * (1 - ratio)).toInt().coerceIn(0, 255)
    )
}
