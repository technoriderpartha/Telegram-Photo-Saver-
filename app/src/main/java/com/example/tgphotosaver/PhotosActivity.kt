package com.example.tgphotosaver

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.tgphotosaver.databinding.ActivityPhotosBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

class PhotosActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_TITLE = "title"
        private const val MIN_PHOTOS_PER_LOAD = 30
        private const val MAX_REQUESTS_PER_LOAD = 8
    }

    private lateinit var binding: ActivityPhotosBinding
    private lateinit var adapter: PhotoAdapter

    private var chatId = 0L
    private var title = ""
    private var nextFromId = 0L
    private var reachedEnd = false
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0L)
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        binding.title.text = title

        adapter = PhotoAdapter(lifecycleScope) { updateButtons() }
        binding.recycler.layoutManager = GridLayoutManager(this, 3)
        binding.recycler.adapter = adapter

        binding.moreButton.setOnClickListener { loadMore() }
        binding.selectAllButton.setOnClickListener { adapter.toggleSelectAll() }
        binding.downloadButton.setOnClickListener { downloadPhotos() }

        updateButtons()
        loadMore()
    }

    private fun loadMore() {
        if (busy || reachedEnd || chatId == 0L) return
        setBusy(true)
        binding.status.setText(R.string.status_loading)

        lifecycleScope.launch {
            try {
                var found = 0
                var requests = 0
                while (found < MIN_PHOTOS_PER_LOAD && requests < MAX_REQUESTS_PER_LOAD && !reachedEnd) {
                    val page = Td.photosBefore(chatId, nextFromId)
                    requests++
                    val oldest = page.oldestMessageId
                    if (oldest == null) reachedEnd = true else nextFromId = oldest
                    adapter.addItems(page.photos)
                    found += page.photos.size
                }
                val count = adapter.itemCount
                binding.status.text = when {
                    count == 0 && reachedEnd -> getString(R.string.status_no_photos)
                    reachedEnd -> getString(R.string.status_loaded_end, count)
                    else -> getString(R.string.status_loaded, count)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                binding.status.text = getString(R.string.status_failed, e.message ?: e.javaClass.simpleName)
            } finally {
                setBusy(false)
            }
        }
    }

    private fun downloadPhotos() {
        if (busy) return
        val list = adapter.selectedPhotos().ifEmpty { adapter.allPhotos() }
        if (list.isEmpty()) return

        setBusy(true)
        binding.progress.max = list.size
        binding.progress.setProgressCompat(0, false)
        binding.progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            var saved = 0
            var skipped = 0
            var failed = 0
            try {
                for ((index, photo) in list.withIndex()) {
                    binding.status.text = getString(R.string.status_downloading, index + 1, list.size)
                    try {
                        val path = Td.downloadFile(photo.fullFileId, 16)
                        if (PhotoSaver.save(applicationContext, File(path), title, photo.messageId)) saved++ else skipped++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed++
                    }
                    binding.progress.setProgressCompat(index + 1, true)
                }
                binding.status.text = getString(
                    R.string.status_done, saved, skipped, failed, PhotoSaver.safeFolderName(title)
                )
            } finally {
                binding.progress.visibility = View.GONE
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        updateButtons()
    }

    private fun updateButtons() {
        val hasItems = adapter.itemCount > 0
        binding.moreButton.isEnabled = !busy && !reachedEnd
        binding.selectAllButton.isEnabled = !busy && hasItems
        binding.selectAllButton.setText(if (adapter.allSelected) R.string.select_none else R.string.select_all)
        binding.downloadButton.isEnabled = !busy && hasItems
        binding.downloadButton.text =
            if (adapter.selectedCount > 0) getString(R.string.download_selected, adapter.selectedCount)
            else getString(R.string.download_all)
    }
}
