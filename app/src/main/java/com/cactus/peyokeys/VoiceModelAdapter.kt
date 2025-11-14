package com.cactus.peyokeys

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cactus.VoiceModel
import com.google.android.material.button.MaterialButton

class VoiceModelAdapter(
    private val models: List<VoiceModel>,
    private val selectedModelSlug: String?,
    private val onDownloadClick: (VoiceModel, Int) -> Unit,
    private val onModelSelected: (VoiceModel) -> Unit
) : RecyclerView.Adapter<VoiceModelAdapter.ViewHolder>() {

    private val downloadingModels = mutableSetOf<String>()
    private var currentSelectedSlug: String? = selectedModelSlug

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val radioSelectModel: RadioButton = view.findViewById(R.id.radio_select_model)
        val textModelName: TextView = view.findViewById(R.id.text_model_name)
        val textModelSize: TextView = view.findViewById(R.id.text_model_size)
        val progressDownload: ProgressBar = view.findViewById(R.id.progress_download)
        val buttonDownload: MaterialButton = view.findViewById(R.id.button_download)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_voice_model, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]
        val isDownloading = downloadingModels.contains(model.slug)

        holder.textModelName.text = model.slug
        holder.textModelSize.text = "${model.size_mb} MB"

        // Radio button
        holder.radioSelectModel.isChecked = model.slug == currentSelectedSlug
        holder.radioSelectModel.isEnabled = model.isDownloaded
        holder.radioSelectModel.setOnClickListener {
            if (model.isDownloaded) {
                val previousSlug = currentSelectedSlug
                currentSelectedSlug = model.slug
                onModelSelected(model)
                notifyDataSetChanged()
            }
        }

        // Download button and progress
        if (isDownloading) {
            holder.progressDownload.visibility = View.VISIBLE
            holder.buttonDownload.visibility = View.INVISIBLE
            holder.buttonDownload.isEnabled = false
        } else if (model.isDownloaded) {
            holder.progressDownload.visibility = View.GONE
            holder.buttonDownload.visibility = View.VISIBLE
            holder.buttonDownload.setIconResource(R.drawable.ic_check_circle)
            holder.buttonDownload.contentDescription = "Model downloaded"
            holder.buttonDownload.isEnabled = false
        } else {
            holder.progressDownload.visibility = View.GONE
            holder.buttonDownload.visibility = View.VISIBLE
            holder.buttonDownload.setIconResource(android.R.drawable.stat_sys_download)
            holder.buttonDownload.contentDescription = "Download model"
            holder.buttonDownload.isEnabled = true
            holder.buttonDownload.setOnClickListener {
                onDownloadClick(model, position)
            }
        }
    }

    override fun getItemCount(): Int = models.size

    fun setDownloading(slug: String, isDownloading: Boolean) {
        if (isDownloading) {
            downloadingModels.add(slug)
        } else {
            downloadingModels.remove(slug)
        }
        val position = models.indexOfFirst { it.slug == slug }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    fun updateModelDownloaded(slug: String) {
        val position = models.indexOfFirst { it.slug == slug }
        if (position >= 0) {
            models[position].isDownloaded = true
            downloadingModels.remove(slug)
            notifyItemChanged(position)
        }
    }
}
