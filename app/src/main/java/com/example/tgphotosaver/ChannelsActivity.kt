package com.example.tgphotosaver

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tgphotosaver.databinding.ActivityChannelsBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ChannelsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChannelsBinding
    private val all = mutableListOf<Channel>()
    private val shown = mutableListOf<Channel>()
    private var loading = false
    private var loaded = false

    private val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun getItemCount(): Int = shown.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val channel = shown[position]
            holder.itemView.findViewById<TextView>(R.id.channelTitle).text = channel.title
            holder.itemView.setOnClickListener {
                startActivity(
                    Intent(this@ChannelsActivity, PhotosActivity::class.java)
                        .putExtra(PhotosActivity.EXTRA_CHAT_ID, channel.id)
                        .putExtra(PhotosActivity.EXTRA_TITLE, channel.title)
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChannelsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.filterInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })
        binding.reloadButton.setOnClickListener { loadChannels() }
        binding.logoutButton.setOnClickListener { logOut() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Td.auth.collect { state ->
                    when (state) {
                        is Auth.Ready -> if (!loaded) loadChannels()
                        is Auth.Starting -> binding.status.setText(R.string.login_connecting)
                        else -> {
                            startActivity(Intent(this@ChannelsActivity, LoginActivity::class.java))
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun loadChannels() {
        if (loading) return
        loading = true
        binding.progress.visibility = View.VISIBLE
        binding.status.setText(R.string.channels_loading)
        lifecycleScope.launch {
            try {
                val channels = Td.loadChannels()
                all.clear()
                all.addAll(channels)
                loaded = true
                applyFilter()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                binding.status.text = getString(R.string.status_failed, e.message ?: "")
            } finally {
                loading = false
                binding.progress.visibility = View.GONE
            }
        }
    }

    private fun applyFilter() {
        val query = binding.filterInput.text?.toString()?.trim().orEmpty().lowercase()
        shown.clear()
        shown.addAll(if (query.isEmpty()) all else all.filter { it.title.lowercase().contains(query) })
        adapter.notifyDataSetChanged()
        binding.status.text = when {
            !loaded -> ""
            all.isEmpty() -> getString(R.string.channels_none)
            else -> getString(R.string.channels_count, shown.size)
        }
    }

    private fun logOut() {
        lifecycleScope.launch {
            try {
                Td.logOut()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                binding.status.text = getString(R.string.status_failed, e.message ?: "")
            }
        }
    }
}
