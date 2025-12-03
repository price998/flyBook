package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.UserEntity
import com.example.myapplication.repository.UserRepository
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UserRepository

    init {
        val userDao = AppDatabase.getDatabase(application).userDao()
        repository = UserRepository(userDao)
    }

    private val _loginResult = MutableLiveData<Result<UserEntity>>()
    val loginResult: LiveData<Result<UserEntity>> = _loginResult
    
    private val _registerResult = MutableLiveData<Result<Boolean>>()
    val registerResult: LiveData<Result<Boolean>> = _registerResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun login(account: String, password: String) {
        if (account.isBlank() || password.isBlank()) {
            _loginResult.value = Result.failure(Exception("账号或密码不能为空"))
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.login(account, password)
                _loginResult.value = result
            } catch (e: Exception) {
                _loginResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun register(account: String, password: String) {
        if (account.isBlank() || password.isBlank()) {
            _registerResult.value = Result.failure(Exception("账号或密码不能为空"))
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.register(account, password)
                _registerResult.value = result
            } catch (e: Exception) {
                _registerResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
