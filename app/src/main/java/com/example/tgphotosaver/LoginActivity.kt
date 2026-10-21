package com.example.tgphotosaver

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.tgphotosaver.databinding.ActivityLoginBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var state: Auth = Auth.Starting
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.actionButton.setOnClickListener { onAction() }
        binding.resetButton.setOnClickListener { Td.resetCredentials() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Td.auth.collect { render(it) }
            }
        }
    }

    private fun render(newState: Auth) {
        state = newState
        binding.credsGroup.visibility = View.GONE
        binding.phoneGroup.visibility = View.GONE
        binding.codeGroup.visibility = View.GONE
        binding.passwordGroup.visibility = View.GONE
        binding.resetButton.visibility = View.GONE
        binding.actionButton.visibility = View.VISIBLE
        binding.progress.visibility = View.GONE

        when (newState) {
            is Auth.Starting -> {
                binding.actionButton.visibility = View.GONE
                binding.progress.visibility = View.VISIBLE
                binding.info.setText(R.string.login_connecting)
            }
            is Auth.NeedCredentials -> {
                binding.credsGroup.visibility = View.VISIBLE
                binding.actionButton.setText(R.string.continue_label)
                binding.info.setText(R.string.login_need_credentials)
            }
            is Auth.WaitPhone -> {
                binding.phoneGroup.visibility = View.VISIBLE
                binding.resetButton.visibility = View.VISIBLE
                binding.actionButton.setText(R.string.send_code)
                binding.info.setText(R.string.login_need_phone)
            }
            is Auth.WaitCode -> {
                binding.codeGroup.visibility = View.VISIBLE
                binding.actionButton.setText(R.string.confirm)
                binding.info.setText(R.string.login_need_code)
            }
            is Auth.WaitPassword -> {
                binding.passwordGroup.visibility = View.VISIBLE
                binding.actionButton.setText(R.string.confirm)
                binding.info.text = getString(R.string.login_need_password, newState.hint)
            }
            is Auth.Ready -> {
                startActivity(Intent(this, ChannelsActivity::class.java))
                finish()
            }
            is Auth.Problem -> {
                binding.actionButton.visibility = View.GONE
                binding.resetButton.visibility = View.VISIBLE
                binding.info.text = newState.text
            }
        }
    }

    private fun onAction() {
        if (busy) return
        binding.error.text = ""
        when (state) {
            is Auth.NeedCredentials -> {
                val id = binding.apiIdInput.text?.toString()?.trim()?.toIntOrNull()
                val hash = binding.apiHashInput.text?.toString()?.trim().orEmpty()
                if (id == null || hash.length < 16) {
                    binding.error.setText(R.string.error_bad_credentials)
                    return
                }
                Td.saveCredentials(id, hash)
            }
            is Auth.WaitPhone -> {
                val phone = binding.phoneInput.text?.toString()?.trim().orEmpty()
                if (phone.length < 6) {
                    binding.error.setText(R.string.error_bad_phone)
                    return
                }
                launchAction { Td.submitPhone(phone) }
            }
            is Auth.WaitCode -> {
                val code = binding.codeInput.text?.toString()?.trim().orEmpty()
                if (code.isEmpty()) return
                launchAction { Td.submitCode(code) }
            }
            is Auth.WaitPassword -> {
                val password = binding.passwordInput.text?.toString().orEmpty()
                if (password.isEmpty()) return
                launchAction { Td.submitPassword(password) }
            }
            else -> Unit
        }
    }

    private fun launchAction(block: suspend () -> Unit) {
        busy = true
        binding.actionButton.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                binding.error.text = e.message ?: getString(R.string.error_generic)
            } finally {
                busy = false
                binding.actionButton.isEnabled = true
                binding.progress.visibility = View.GONE
            }
        }
    }
}
