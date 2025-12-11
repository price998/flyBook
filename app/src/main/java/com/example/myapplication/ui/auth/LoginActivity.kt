package com.example.myapplication.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.databinding.ActivityLoginBinding
import com.example.myapplication.ui.main.MainActivity

/**
 * 登录 Activity（界面层）
 *
 * 这是登录模块的界面层，负责：
 * 1. 显示登录界面
 * 2. 接收用户输入
 * 3. 与 ViewModel 交互
 * 4. 根据数据变化更新界面
 *
 * MVVM 架构中的角色：
 * LoginActivity（View层）→ 观察 → AccountViewModel（ViewModel层）
 *                        ← 通知 ←
 *
 * AppCompatActivity：Android 的 Activity 基类，提供向后兼容的特性
 */
class LoginActivity : AppCompatActivity() {

    /**
     * 设置状态栏样式
     * 统一使用白色背景 + 深色图标
     */
    private fun setupStatusBar() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        
        // 设置状态栏图标为深色（适配白色背景）
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true  // 深色图标
            isAppearanceLightNavigationBars = true  // 深色导航栏图标
        }
    }

    /**
     * ViewModel 实例
     *
     * by viewModels()：Kotlin 委托属性，自动创建和管理 ViewModel
     *
     * 优势：
     * - 自动处理生命周期：屏幕旋转时 ViewModel 不会被销毁
     * - 懒加载：只有第一次使用时才创建
     * - 线程安全：确保只创建一个实例
     *
     * private：只在这个 Activity 内部使用
     * val：不可变引用（但 ViewModel 内部的数据可以变化）
     */
    private val viewModel: AccountViewModel by viewModels()
    
    /**
     * ViewBinding 实例
     * 用于访问布局中的所有控件，替代 findViewById
     */
    private lateinit var binding: ActivityLoginBinding

    /**
     * 当前是否为注册模式
     * true = 注册模式，false = 登录模式
     */
    private var isRegisterMode = false

    /**
     * 临时存储注册时的账号密码，用于注册成功后自动登录
     */
    private var pendingUsername: String? = null
    private var pendingPassword: String? = null

    /**
     * Activity 创建时调用的方法
     *
     * 这是 Activity 的生命周期方法，Activity 被创建时会自动调用。
     *
     * @param savedInstanceState 保存的状态数据（屏幕旋转时可以恢复数据）
     *
     * 执行流程：
     * 1. 调用父类的 onCreate（必须）
     * 2. 加载布局文件（activity_login.xml）
     * 3. 初始化界面控件
     * 4. 设置 ViewModel 观察者
     * 5. 检查登录状态
     *
     * override：重写父类方法
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置状态栏样式（白色背景 + 深色图标）
        setupStatusBar()

        // 初始化 ViewBinding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化所有界面控件和事件监听器
        initViews()

        // 设置 ViewModel 的数据观察者（当数据变化时自动更新界面）
        observeViewModel()

        // 检查是否已有账户登录
        viewModel.checkLoginState()
    }

    /**
     * 初始化界面控件
     *
     * 功能：设置按钮点击事件监听器
     * 使用 ViewBinding 访问控件，无需 findViewById
     */
    private fun initViews() {
        // ==================== 设置事件监听器 ====================

        /**
         * 登录/注册按钮点击事件
         *
         * setOnClickListener：设置点击监听器，点击时执行大括号里的代码
         *
         * 执行逻辑：
         * 1. 获取用户输入的用户名和密码
         * 2. 验证输入是否为空
         * 3. 根据当前模式（登录/注册）执行相应操作
         */
        binding.btnLogin.setOnClickListener {
            // 获取输入框的文本并去除首尾空格
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (isRegisterMode) {
                // ==================== 注册模式 ====================
                val passwordConfirm = binding.etPasswordConfirm.text.toString().trim()

                when {
                    username.isEmpty() -> {
                        binding.tilUsername.error = "请输入账号/手机号"
                    }
                    password.isEmpty() -> {
                        binding.tilPassword.error = "请输入密码"
                    }
                    passwordConfirm.isEmpty() -> {
                        binding.tilPasswordConfirm.error = "请确认密码"
                    }
                    password != passwordConfirm -> {
                        binding.tilPasswordConfirm.error = "两次密码不一致"
                    }
                    else -> {
                        // 清除错误提示
                        binding.tilUsername.error = null
                        binding.tilPassword.error = null
                        binding.tilPasswordConfirm.error = null

                        // 检查用户名是否已存在
                        viewModel.checkUsernameExists(username)
                    }
                }
            } else {
                // ==================== 登录模式 ====================
                when {
                    username.isEmpty() -> {
                        binding.tilUsername.error = "请输入账号/手机号"
                    }
                    password.isEmpty() -> {
                        binding.tilPassword.error = "请输入密码"
                    }
                    else -> {
                        // 清除错误提示
                        binding.tilUsername.error = null
                        binding.tilPassword.error = null

                        // 调用 ViewModel 执行登录
                        viewModel.login(username, password)
                    }
                }
            }
        }

        /**
         * 进入应用按钮点击事件
         *
         * 功能：已登录用户点击后直接进入对话页面
         */
        binding.btnEnterApp.setOnClickListener {
            val intent = android.content.Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // 关闭登录页面
        }

        /**
         * 登出按钮点击事件
         *
         * 直接调用 ViewModel 的 logout 方法
         */
        binding.btnLogout.setOnClickListener {
            viewModel.logout()
        }

        /**
         * "忘记密码"点击事件
         */
        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "忘记密码功能开发中...", Toast.LENGTH_SHORT).show()
        }

        /**
         * 模式切换点击事件（登录 ↔ 注册）
         */
        binding.tvModeSwitch.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateUIMode()
        }
    }

    /**
     * 设置 ViewModel 数据观察者
     *
     * 功能：监听 ViewModel 中 LiveData 的数据变化，当数据变化时自动更新界面
     *
     * 什么是观察者模式？
     * - 被观察者（LiveData）：当数据变化时，通知所有观察者
     * - 观察者（Activity）：收到通知后，执行相应的界面更新
     *
     * observe() 方法：
     * - 第一个参数 this：生命周期所有者（Activity），当 Activity 销毁时自动取消观察
     * - 第二个参数 { }：数据变化时执行的代码块（Lambda 表达式）
     * - Lambda 参数：变化后的新数据
     *
     * 优势：
     * - 自动管理生命周期：Activity 销毁时自动取消观察，避免内存泄漏
     * - 自动更新界面：数据变化时自动调用观察者代码，无需手动刷新
     * - 线程安全：LiveData 确保在主线程通知观察者
     */
    private fun observeViewModel() {
        /**
         * 观察账户信息变化
         *
         * 触发时机：
         * - App 启动时（checkLoginState 方法）
         * - 登录成功后
         * - 登出成功后
         *
         * 参数 account：当前账户信息（可能为 null）
         * - null：表示未登录
         * - AccountInfo 对象：表示已登录，包含用户名和账户ID
         *
         * 执行逻辑：
         * 根据账户信息是否为 null，显示不同的界面状态
         */
        viewModel.accountInfo.observe(this) { account ->
            if (account != null) {
                // 账户不为空 = 已登录
                // 显示已登录界面（显示账户信息和操作选项）
                showLoggedInState(account.username, account.accountId)
            } else {
                // 账户为空 = 未登录
                // 显示登录界面（隐藏账户信息，显示登录表单）
                showLoggedOutState()
            }
        }

        /**
         * 观察登录结果
         *
         * 触发时机：
         * - 用户点击"登录"按钮后，登录操作完成时
         *
         * 参数 success：登录是否成功
         * - true：登录成功
         * - false：登录失败
         *
         * 执行逻辑：
         * 1. 显示提示消息（成功或失败）
         * 2. 如果成功，清空输入框
         */
        viewModel.loginResult.observe(this) { success ->
            if (success) {
                // 登录成功
                Toast.makeText(this, "登录成功！", Toast.LENGTH_SHORT).show()

                // 清空输入框（提升用户体验）
                binding.etUsername.setText("")
                binding.etPassword.setText("")
                
                // 跳转到对话页面
                val intent = android.content.Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish() // 关闭登录页面，防止返回
            } else {
                // 登录失败
                Toast.makeText(this, "登录失败，请检查账号密码", Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * 观察登出结果
         *
         * 触发时机：
         * - 用户点击"登出"按钮后，登出操作完成时
         *
         * 参数 success：登出是否成功
         *
         * 执行逻辑：
         * 显示登出成功的提示消息
         * 注意：界面切换由 accountInfo 观察者负责，这里只显示提示
         */
        viewModel.logoutResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "已成功登出", Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * 观察注册结果
         *
         * 触发时机：
         * - 用户点击"注册"按钮后，注册操作完成时
         *
         * 参数 success：注册是否成功
         * - true：注册成功
         * - false：注册失败
         *
         * 执行逻辑：
         * 1. 显示提示消息（成功或失败）
         * 2. 如果成功，自动使用注册的账号密码进行登录
         */
        viewModel.registerResult.observe(this) { success ->
            if (success) {
                // 注册成功，显示提示
                Toast.makeText(this, "注册成功！正在自动登录...", Toast.LENGTH_SHORT).show()

                // 使用注册时的账号密码自动登录
                pendingUsername?.let { username ->
                    pendingPassword?.let { password ->
                        viewModel.login(username, password)
                    }
                }

                // 清空临时存储的账号密码
                pendingUsername = null
                pendingPassword = null
            } else {
                // 注册失败
                Toast.makeText(this, "注册失败，请稍后重试", Toast.LENGTH_SHORT).show()
                
                // 清空临时存储的账号密码
                pendingUsername = null
                pendingPassword = null
            }
        }

        /**
         * 观察用户名检查结果
         *
         * 触发时机：
         * - 在注册模式下，用户点击"注册"按钮后，检查用户名是否已存在
         *
         * 参数 exists：用户名是否已存在
         * - true：已存在
         * - false：不存在
         *
         * 执行逻辑：
         * 1. 如果用户名已存在：提示用户，并询问是否前往登录
         * 2. 如果用户名不存在：执行注册
         */
        viewModel.usernameCheckResult.observe(this) { exists ->
            if (exists) {
                // 用户名已存在，提示用户并询问是否前往登录
                android.app.AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("账号已存在，是否前往登录？")
                    .setPositiveButton("前往登录") { _, _ ->
                        // 切换到登录模式
                        isRegisterMode = false
                        updateUIMode()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                // 用户名不存在，执行注册
                val username = binding.etUsername.text.toString().trim()
                val password = binding.etPassword.text.toString().trim()
                
                // 保存账号密码，用于注册成功后自动登录
                pendingUsername = username
                pendingPassword = password
                
                viewModel.register(username, password)
            }
        }
    }

    /**
     * 更新界面模式（登录/注册）
     *
     * 功能：根据 isRegisterMode 的值，切换登录和注册界面
     *
     * 界面变化：
     * - 登录模式：隐藏确认密码框，显示"忘记密码"，按钮文字为"登录"，提示文字为"没有账号？立即注册"
     * - 注册模式：显示确认密码框，隐藏"忘记密码"，按钮文字为"注册"，提示文字为"已有账号？立即登录"
     */
    private fun updateUIMode() {
        if (isRegisterMode) {
            // ==================== 注册模式 ====================
            binding.tilPasswordConfirm.visibility = View.VISIBLE  // 显示确认密码框
            binding.tvForgotPassword.visibility = View.GONE       // 隐藏"忘记密码"
            binding.btnLogin.text = "注册"                        // 按钮文字改为"注册"
            binding.tvModeTip.text = "已有账号？"                  // 提示文字
            binding.tvModeSwitch.text = "立即登录"                // 切换文字
        } else {
            // ==================== 登录模式 ====================
            binding.tilPasswordConfirm.visibility = View.GONE     // 隐藏确认密码框
            binding.tvForgotPassword.visibility = View.VISIBLE    // 显示"忘记密码"
            binding.btnLogin.text = "登录"                        // 按钮文字改为"登录"
            binding.tvModeTip.text = "没有账号？"                  // 提示文字
            binding.tvModeSwitch.text = "立即注册"                // 切换文字
        }

        // 清空所有输入框和错误提示
        binding.etUsername.setText("")
        binding.etPassword.setText("")
        binding.etPasswordConfirm.setText("")
        binding.tilUsername.error = null
        binding.tilPassword.error = null
        binding.tilPasswordConfirm.error = null
    }

    /**
     * 显示已登录状态的界面
     *
     * 功能：当用户已登录时，隐藏登录表单，显示账户信息和登出按钮
     *
     * @param username 用户名
     * @param accountId 账户ID
     *
     * 界面变化：
     * - 隐藏：用户名输入框、密码输入框、登录按钮、注册区域、第三方登录区域
     * - 显示：账户信息文本、登出按钮
     *
     * View.VISIBLE 和 View.GONE 的区别：
     * - VISIBLE：可见，占据空间
     * - INVISIBLE：不可见，但占据空间
     * - GONE：不可见，不占据空间（完全移除）
     */
    private fun showLoggedInState(username: String, accountId: String) {
        // ==================== 隐藏登录表单 ====================
        binding.tilUsername.visibility = View.GONE
        binding.tilPassword.visibility = View.GONE
        binding.tilPasswordConfirm.visibility = View.GONE
        binding.tvForgotPassword.visibility = View.GONE
        binding.btnLogin.visibility = View.GONE
        binding.llRegister.visibility = View.GONE
        binding.llThirdPartyLogin.visibility = View.GONE

        // ==================== 显示账户信息 ====================
        binding.tvAccountInfo.visibility = View.VISIBLE
        binding.tvAccountInfo.text = getString(
            com.example.myapplication.R.string.welcome_back_format,
            username,
            accountId
        )
        binding.btnEnterApp.visibility = View.VISIBLE
        binding.btnLogout.visibility = View.VISIBLE
    }

    /**
     * 显示未登录状态的界面
     *
     * 功能：当用户未登录时，显示登录表单，隐藏账户信息
     *
     * 界面变化：
     * - 显示：用户名输入框、密码输入框、登录按钮、注册区域、第三方登录区域
     * - 隐藏：账户信息文本、登出按钮
     *
     * 这个方法与 showLoggedInState 的显示/隐藏逻辑正好相反
     */
    private fun showLoggedOutState() {
        // ==================== 显示登录表单 ====================
        binding.tilUsername.visibility = View.VISIBLE
        binding.tilPassword.visibility = View.VISIBLE
        binding.tvForgotPassword.visibility = View.VISIBLE
        binding.btnLogin.visibility = View.VISIBLE
        binding.llRegister.visibility = View.VISIBLE
        binding.llThirdPartyLogin.visibility = View.VISIBLE

        // ==================== 隐藏账户信息 ====================
        binding.tvAccountInfo.visibility = View.GONE
        binding.btnEnterApp.visibility = View.GONE
        binding.btnLogout.visibility = View.GONE

        // ==================== 重置为登录模式 ====================
        isRegisterMode = false
        updateUIMode()
    }
}
