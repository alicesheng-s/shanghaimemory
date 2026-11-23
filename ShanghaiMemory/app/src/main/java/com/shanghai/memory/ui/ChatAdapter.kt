package com.shanghai.memory.ui

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shanghai.memory.R
import com.shanghai.memory.data.ChatMessage
import com.shanghai.memory.databinding.ItemChatMessageBinding
import com.shanghai.memory.ui.render.MarkdownLite
import com.shanghai.memory.ui.theme.AppFonts
import com.shanghai.memory.ui.theme.ThemeState

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    class Holder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            val ctx = binding.root.context
            // 用最终色板而不是 ?attr：用户自定义主色时，控件自带的静态色不是最终色
            val colors = ThemeState.colors

            binding.txtRole.text = when (message.role) {
                ChatMessage.Role.USER -> "我"
                ChatMessage.Role.SYSTEM -> "系统"
                ChatMessage.Role.ASSISTANT -> "讲述者"
            } + if (message.stage.isNotBlank()) " · ${message.stage}" else ""
            binding.markBar.visibility =
                if (message.role == ChatMessage.Role.ASSISTANT) View.VISIBLE else View.GONE
            binding.markBar.setBackgroundColor(colors.accent)

            binding.txtBody.setTextColor(
                when {
                    message.isError -> colors.danger
                    message.role == ChatMessage.Role.USER -> colors.accent
                    message.role == ChatMessage.Role.SYSTEM -> colors.textMuted
                    else -> colors.text
                }
            )

            // 还没吐字的时候，把进度（含已等待秒数）当作正文显示，
            // 否则用户看到一片空白，会以为卡住了
            val waiting = message.text.isBlank() && message.streaming
            val body = message.text.ifBlank {
                if (waiting) {
                    message.stage.ifBlank { ctx.getString(R.string.chat_stage_prepare) }
                } else {
                    ""
                }
            }
            binding.txtBody.text = MarkdownLite.toSpanned(body, colors)
            if (waiting) binding.txtBody.setTextColor(colors.textMuted)

            // 字体与字号跟着设置走；取不到就保持布局里的默认字体
            AppFonts.of(ctx).get(ThemeState.font, ThemeState.importedFontPath)?.let {
                binding.txtBody.typeface = it
            }
            binding.txtBody.setTextSize(
                TypedValue.COMPLEX_UNIT_SP, BASE_TEXT_SP * ThemeState.fontScale
            )

            val sources = message.sources
            if (sources.isEmpty()) {
                binding.txtSources.visibility = View.GONE
            } else {
                binding.txtSources.visibility = View.VISIBLE
                binding.txtSources.setTextColor(colors.textMuted)
                binding.txtSources.text = buildString {
                    append("资料：")
                    append(
                        sources.take(5).joinToString("；") { hit ->
                            val site = hit.siteName?.takeIf { it.isNotBlank() }
                            if (site != null) "${hit.title}（$site）" else hit.title
                        }
                    )
                    if (message.streaming) append("  · 正在写…")
                }
            }
        }
    }

    companion object {
        /** 与 item_chat_message.xml 里的正文字号保持一致，改布局要一起改 */
        private const val BASE_TEXT_SP = 15f

        private val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
                oldItem == newItem
        }
    }
}
