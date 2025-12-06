package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapplication.repository.AccountRepository
import com.example.myapplication.model.AccountInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 账户 ViewModel（视图模型）
 *
 * 什么是 ViewModel？
 * ViewModel 是 MVVM 架构中的"业务逻辑层"，连接 View（界面）和 Model（数据）。
 *
 * MVVM 架构流程：
 * View（LoginActivity） ↔ ViewModel（AccountViewModel） ↔ Repository → Database
 *     ↑                           ↑
 *     观察 LiveData              业务逻辑处理
 *
 * ViewModel 的职责：
 * 1. 存储界面需要的数据（如账户信息）
 * 2. 处理业务逻辑（如登录、登出）
 * 3. 调用 Repository 获取数据
 * 4. 通过 LiveData 通知界面更新
 *
 * ViewModel 的优势：
 * 1. 生命周期感知：屏幕旋转时数据不会丢失
 * 2. 数据共享：多个 Fragment 可以共享同一个 ViewModel
 * 3. 解耦：界面和业务逻辑分离，便于测试和维护
 *
 * AndroidViewModel vs ViewModel：
 * - AndroidViewModel：可以访问 Application Context（应用上下文）
 * - ViewModel：普通 ViewModel，不能访问 Context
 *
 * 这里继承 AndroidViewModel 是因为需要 Context 来创建 Repository
 */
class AccountViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * 数据仓库实例
     *
     * 通过 Repository 访问数据，ViewModel 不直接操作数据库。
     * private：只在这个类内部使用
     */
    private val repository: AccountRepository = AccountRepository(application)

    // ==================== LiveData 数据容器 ====================
    //
    // 什么是 LiveData？
    // LiveData 是一个可观察的数据容器，当数据变化时会自动通知观察者（Activity/Fragment）。
    //
    // MutableLiveData vs LiveData：
    // - MutableLiveData：可变的，可以修改数据（setValue/postValue）
    // - LiveData：只读的，只能观察数据变化
    //
    // 为什么要分开？
    // - 私有的 _accountInfo（带下划线）：可变的，只有 ViewModel 内部能修改
    // - 公开的 accountInfo：只读的，Activity 只能观察，不能修改
    // 这样可以保护数据，避免外部随意修改

    /**
     * 账户信息（私有，可变）
     *
     * 存储当前登录账户的信息，只有 ViewModel 内部能修改。
     */
    private val _accountInfo = MutableLiveData<AccountInfo?>()

    /**
     * 账户信息（公开，只读）
     *
     * 提供给 Activity 观察，当账户信息变化时，界面会自动更新。
     * AccountInfo? 表示可能为 null（未登录时为 null）
     */
    val accountInfo: LiveData<AccountInfo?> = _accountInfo

    /**
     * 登录结果（私有，可变）
     */
    private val _loginResult = MutableLiveData<Boolean>()

    /**
     * 登录结果（公开，只读）
     *
     * 提供给 Activity 观察，当登录操作完成时，会收到结果（true=成功，false=失败）。
     */
    val loginResult: LiveData<Boolean> = _loginResult

    /**
     * 登出结果（私有，可变）
     */
    private val _logoutResult = MutableLiveData<Boolean>()

    /**
     * 登出结果（公开，只读）
     *
     * 提供给 Activity 观察，当登出操作完成时，会收到结果。
     */
    val logoutResult: LiveData<Boolean> = _logoutResult

    /**
     * 注册结果（私有，可变）
     */
    private val _registerResult = MutableLiveData<Boolean>()

    /**
     * 注册结果（公开，只读）
     *
     * 提供给 Activity 观察，当注册操作完成时，会收到结果（true=成功，false=失败）。
     */
    val registerResult: LiveData<Boolean> = _registerResult

    /**
     * 用户名检查结果（私有，可变）
     */
    private val _usernameCheckResult = MutableLiveData<Boolean>()

    /**
     * 用户名检查结果（公开，只读）
     *
     * 提供给 Activity 观察，当检查用户名是否存在时，会收到结果（true=已存在，false=不存在）。
     */
    val usernameCheckResult: LiveData<Boolean> = _usernameCheckResult

    // ==================== 协程相关 ====================
    //
    // 什么是协程（Coroutine）？
    // 协程是 Kotlin 提供的轻量级线程，用于执行耗时操作（如数据库、网络请求）。
    //
    // 为什么需要协程？
    // 数据库操作不能在主线程（UI线程）执行，否则会卡住界面（ANR错误）。
    // 协程可以在后台线程执行操作，完成后再通知主线程更新界面。

    /**
     * 协程任务（Job）
     *
     * 用于管理协程的生命周期，可以取消所有正在执行的协程。
     */
    private val ioJob = Job()

    /**
     * 协程作用域（Scope）
     *
     * Dispatchers.IO：指定在 IO 线程执行（适合数据库、文件、网络操作）
     * ioJob：关联到 Job，方便统一取消
     *
     * Dispatchers 类型说明：
     * - Dispatchers.Main：主线程（UI线程）
     * - Dispatchers.IO：IO线程（数据库、网络）
     * - Dispatchers.Default：默认线程（CPU密集型任务）
     */
    private val ioScope = CoroutineScope(Dispatchers.IO + ioJob)

    // ==================== 业务方法 ====================

    /**
     * 检查登录状态
     *
     * 功能：检查是否有账户已登录，并获取账户信息
     *
     * 调用时机：
     * - Activity 创建时调用，用于判断是显示登录界面还是已登录界面
     *
     * 执行流程：
     * 1. 在 IO 线程启动协程（避免阻塞主线程）
     * 2. 调用 Repository 检查是否已登录
     * 3. 如果已登录，获取账户信息并更新 LiveData
     * 4. 如果未登录，将 LiveData 设为 null
     * 5. LiveData 变化会自动通知 Activity 更新界面
     *
     * ioScope.launch { }：在 IO 线程启动协程
     * postValue()：在后台线程更新 LiveData（会自动切换到主线程通知观察者）
     */
    fun checkLoginState() {
        ioScope.launch {
            // 查询是否已登录
            val isLoggedIn = repository.isLoggedIn()

            if (isLoggedIn) {
                // 已登录：获取账户信息
                val account = repository.getAccount()
                _accountInfo.postValue(account)  // 更新 LiveData，界面会收到通知
            } else {
                // 未登录：设为 null
                _accountInfo.postValue(null)
            }
        }
    }

    /**
     * 登录方法
     *
     * 功能：执行登录操作
     *
     * @param username 用户名
     * @param password 密码
     *
     * 执行流程：
     * 1. 在 IO 线程启动协程
     * 2. 调用 Repository 执行登录
     * 3. 将登录结果通过 loginResult LiveData 通知界面
     * 4. 如果登录成功，获取账户信息并更新 accountInfo
     * 5. Activity 观察到变化后，会显示相应的提示信息
     *
     * 为什么要用协程？
     * 数据库操作是耗时操作，不能在主线程执行，必须在后台线程。
     */
    fun login(username: String, password: String) {
        ioScope.launch {
            // 执行登录操作
            val success = repository.login(username, password)

            // 通知界面登录结果
            _loginResult.postValue(success)

            // 如果登录成功，获取账户信息
            if (success) {
                val account = repository.getAccount()
                _accountInfo.postValue(account)
            }
        }
    }

    /**
     * 登出方法
     *
     * 功能：执行登出操作
     *
     * 执行流程：
     * 1. 在 IO 线程启动协程
     * 2. 调用 Repository 执行登出
     * 3. 将登出结果通过 logoutResult LiveData 通知界面
     * 4. 如果登出成功，将 accountInfo 设为 null
     * 5. Activity 观察到变化后，会显示登录界面
     */
    fun logout() {
        ioScope.launch {
            // 执行登出操作
            val success = repository.logout()

            // 通知界面登出结果
            _logoutResult.postValue(success)

            // 如果登出成功，清空账户信息
            if (success) {
                _accountInfo.postValue(null)
            }
        }
    }

    /**
     * 注册方法
     *
     * 功能：执行注册操作
     *
     * @param username 用户名
     * @param password 密码
     *
     * 执行流程：
     * 1. 在 IO 线程启动协程
     * 2. 调用 Repository 执行注册
     * 3. 将注册结果通过 registerResult LiveData 通知界面
     * 4. Activity 观察到变化后，会显示相应的提示信息
     */
    fun register(username: String, password: String) {
        ioScope.launch {
            // 执行注册操作
            val success = repository.register(username, password)

            // 通知界面注册结果
            _registerResult.postValue(success)
        }
    }

    /**
     * 检查用户名是否已存在
     *
     * 功能：查询用户名是否已被使用
     *
     * @param username 用户名
     *
     * 执行流程：
     * 1. 在 IO 线程启动协程
     * 2. 调用 Repository 检查用户名
     * 3. 将检查结果通过 usernameCheckResult LiveData 通知界面
     * 4. Activity 观察到变化后，可以显示提示或执行相应操作
     */
    fun checkUsernameExists(username: String) {
        ioScope.launch {
            // 检查用户名是否存在
            val exists = repository.isUsernameExists(username)

            // 通知界面检查结果
            _usernameCheckResult.postValue(exists)
        }
    }

    /**
     * 更新账户信息
     *
     * 功能：修改当前登录账户的用户名或密码
     *
     * @param username 新的用户名（null 表示不修改）
     * @param password 新的密码（null 表示不修改）
     *
     * 执行流程：
     * 1. 在 IO 线程启动协程
     * 2. 调用 Repository 更新账户信息
     * 3. 重新获取账户信息并更新 LiveData
     * 4. 界面会显示更新后的信息
     *
     * 默认参数：= null
     * 调用时可以只传一个参数，另一个会使用默认值 null
     * 例如：updateAccount(username = "新名字") // 只修改用户名
     */
    @Suppress("unused")
    fun updateAccount(username: String? = null, password: String? = null) {
        ioScope.launch {
            // 更新账户信息
            repository.updateAccount(username, password)

            // 重新获取最新的账户信息
            val account = repository.getAccount()
            _accountInfo.postValue(account)
        }
    }

    /**
     * 清理方法
     *
     * 功能：当 ViewModel 被销毁时，清理资源
     *
     * 调用时机：
     * - Activity 被销毁时（如用户关闭应用）
     * - 注意：屏幕旋转不会销毁 ViewModel
     *
     * 为什么要取消协程？
     * 如果不取消，协程会继续执行，可能导致内存泄漏。
     *
     * override：重写父类方法
     * super.onCleared()：先调用父类的清理方法
     */
    override fun onCleared() {
        super.onCleared()
        // 取消所有正在执行的协程
        ioJob.cancel()
    }
}
