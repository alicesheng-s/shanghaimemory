package com.shanghai.memory.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shanghai.memory.data.FamousLandmark
import com.shanghai.memory.data.SpotCandidate
import com.shanghai.memory.databinding.ItemSpotBinding
import com.shanghai.memory.ui.theme.AppFonts
import com.shanghai.memory.ui.theme.ThemeState

data class FamousLandmarkRow(
    val landmark: FamousLandmark,
    val candidate: SpotCandidate,
)

class FamousLandmarkAdapter(
    private val onClick: (FamousLandmarkRow) -> Unit,
) : ListAdapter<FamousLandmarkRow, FamousLandmarkAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemSpotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemSpotBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: FamousLandmarkRow) {
            val colors = ThemeState.colors
            val ctx = binding.root.context
            val en = ThemeState.isEnglish
            val spot = row.candidate

            binding.txtSpotName.text = row.landmark.name(en)
            binding.txtSpotArea.text = row.landmark.area(en)
            binding.txtSpotBlurb.text = row.landmark.blurb(en)

            val dist = spot.distanceText
            binding.txtSpotDist.text = when {
                dist.isNotBlank() -> if (en) "$dist away · Offline guide" else "距你约 $dist · 离线可读"
                spot.located -> if (en) "Located · Offline guide" else "已定位 · 离线可读"
                else -> if (en) "Offline guide ready" else "离线图文介绍 · 点击查看"
            }
            binding.txtSpotDist.setTextColor(colors.accentBright)

            binding.btnAdd.text = "›"
            binding.btnAdd.setTextColor(colors.accent)
            val bg = binding.btnAdd.background?.mutate()
            if (bg is android.graphics.drawable.GradientDrawable) {
                bg.setColor(colors.bgRaise)
            }

            binding.btnAdd.setOnClickListener { onClick(row) }
            binding.root.setOnClickListener { onClick(row) }

            val font = AppFonts.of(ctx).get(ThemeState.font, ThemeState.importedFontPath)
            binding.txtSpotName.typeface = font
            binding.txtSpotBlurb.typeface = font
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<FamousLandmarkRow>() {
            override fun areItemsTheSame(old: FamousLandmarkRow, new: FamousLandmarkRow) =
                old.landmark.id == new.landmark.id

            override fun areContentsTheSame(old: FamousLandmarkRow, new: FamousLandmarkRow) =
                old == new
        }
    }
}
