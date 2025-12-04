package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.databinding.ActivityLoginBinding
import com.example.myapplication.viewmodel.LoginViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        setupUI()
        setupObservers()
    }

    private fun setupUI() {
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString()
            val password = binding.etPassword.text.toString()

            if (isLoginMode) {
                viewModel.login(username, password)
            } else {
                viewModel.register(username, password)
            }
        }

        binding.tvModeSwitch.setOnClickListener {
            toggleMode()
        }
    }

    private fun toggleMode() {
        isLoginMode = !isLoginMode
        if (isLoginMode) {
            binding.titleText.text = "欢迎回来" // 或使用资源字符串
            binding.btnLogin.text = "登录"
            binding.tvModeTip.text = "没有账号？"
            binding.tvModeSwitch.text = "立即注册"
        } else {
            binding.titleText.text = "创建账号"
            binding.btnLogin.text = "注册"
            binding.tvModeTip.text = "已有账号？"
            binding.tvModeSwitch.text = "立即登录"
        }
    }

    private fun setupObservers() {
        viewModel.loginResult.observe(this) { result ->
            if (result.isSuccess) {
                android.util.Log.d("Login", "登录成功")
                Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, DialogueActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                val exception = result.exceptionOrNull()
                val errorMessage = exception?.message ?: "登录失败"
                android.util.Log.e("Login", "登录失败", exception)
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.registerResult.observe(this) { result ->
            if (result.isSuccess) {
                android.util.Log.d("Register", "注册成功")
                Toast.makeText(this, "注册成功，请登录", Toast.LENGTH_SHORT).show()
                toggleMode() // 切换回登录模式
            } else {
                val exception = result.exceptionOrNull()
                val errorMessage = exception?.message ?: "注册失败"
                android.util.Log.e("Register", "注册失败", exception)
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            binding.btnLogin.isEnabled = !isLoading
        }
    }
}
