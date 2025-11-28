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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        binding.loginButton.setOnClickListener {
            val username = binding.accountEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            viewModel.login(username, password)
        }

        viewModel.loginResult.observe(this) { result ->
            if (result.isSuccess) {
                Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
                // Navigate to DialogueActivity
                val intent = Intent(this, DialogueActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "登录失败"
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
