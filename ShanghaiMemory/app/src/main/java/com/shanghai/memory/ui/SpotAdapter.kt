package com.shanghai.memory.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shanghai.memory.R
import com.shanghai.memory.data.SpotCandidate
import com.shanghai.memory.databinding.ItemSpotBinding
import com.shanghai.memory.ui.theme.AppFonts
import com.shanghai.memory.ui.theme.ThemeState

/** 网格里的一格：候选地点 + 是否已被勾选 */
data class SpotRow(
    val spot: SpotCandidate,
    val selected: Boolean,
)

class SpotAdapter(
    private val onToggle: (SpotCandidate) -> Unit,
) : ListAdapter<SpotRow, SpotAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemSpotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemSpotBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: SpotRow) {
            val spot = row.spot
            val colors = ThemeState.colors
            val ctx = binding.root.context

            val en = ThemeState.isEnglish
            binding.txtSpotName.text = spot.name
            binding.txtSpotArea.text = spot.area.ifBlank { if (en) "Shanghai" else "上海" }
            binding.txtSpotBlurb.text = spot.blurb.ifBlank { if (en) "No intro yet" else "暂无简介" }

            val dist = spot.distanceText
            binding.txtSpotDist.text = when {
                dist.isNotBlank() -> if (en) "$dist away" else "距你约 $dist"
                spot.located -> if (en) "Pinned" else "已定位"
                spot.locateAttempted -> if (en) "Location unavailable" else "暂未定位到"
                else -> if (en) "Locating…" else "正在查找位置…"
            }
            binding.txtSpotDist.setTextColor(colors.accentBright)

            // 选中态：加号变对勾、描边换成实心主色
            binding.btnAdd.text = if (row.selected) "✓" else ctx.getString(R.string.route_add)
            binding.btnAdd.setTextColor(if (row.selected) colors.onAccent else colors.accent)
            val bg = binding.btnAdd.background?.mutate()
            if (bg is android.graphics.drawable.GradientDrawable) {
                bg.setColor(if (row.selected) colors.accent else colors.bgRaise)
            }

            binding.btnAdd.setOnClickListener { onToggle(spot) }
            binding.root.setOnClickListener { onToggle(spot) }

            val font = AppFonts.of(ctx).get(ThemeState.font, ThemeState.importedFontPath)
            binding.txtSpotName.typeface = font
            binding.txtSpotBlurb.typeface = font
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SpotRow>() {
            override fun areItemsTheSame(old: SpotRow, new: SpotRow) = old.spot.name == new.spot.name
            override fun areContentsTheSame(old: SpotRow, new: SpotRow) = old == new
        }
    }
}
