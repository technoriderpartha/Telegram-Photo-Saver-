package com.example.tgphotosaver

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.tgphotosaver.databinding.ItemPhotoBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class PhotoAdapter(
    private val scope: CoroutineScope,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<PhotoAdapter.VH>() {

    class VH(val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        var job: Job? = null
    }

    private val items = mutableListOf<TgPhoto>()
    private val known = HashSet<Long>()
    private val selected = HashSet<Long>()

    val selectedCount: Int get() = selected.size
    val allSelected: Boolean get() = items.isNotEmpty() && selected.size == items.size

    fun selectedPhotos(): List<TgPhoto> = items.filter { it.messageId in selected }
    fun allPhotos(): List<TgPhoto> = items.toList()

    fun addItems(photos: List<TgPhoto>) {
        val fresh = photos.filter { known.add(it.messageId) }
        if (fresh.isEmpty()) return
        val start = items.size
        items.addAll(fresh)
        notifyItemRangeInserted(start, fresh.size)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun toggleSelectAll() {
        if (allSelected) selected.clear() else items.forEach { selected.add(it.messageId) }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    private fun toggle(position: Int) {
        val id = items[position].messageId
        if (!selected.remove(id)) selected.add(id)
        notifyItemChanged(position)
        onSelectionChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val photo = items[position]
        holder.job?.cancel()
        holder.binding.image.setImageDrawable(null)
        holder.job = scope.launch {
            try {
                val path = Td.downloadFile(photo.thumbFileId, 8)
                holder.binding.image.load(File(path))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // leave the placeholder if a thumbnail fails
            }
        }
        holder.binding.selectedOverlay.visibility =
            if (photo.messageId in selected) View.VISIBLE else View.GONE
        holder.binding.root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) toggle(pos)
        }
    }

    override fun onViewRecycled(holder: VH) {
        holder.job?.cancel()
        super.onViewRecycled(holder)
    }
}
