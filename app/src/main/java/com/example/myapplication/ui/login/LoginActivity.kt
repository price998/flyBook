package com.example.myapplication.ui.login

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.viewmodel.AccountViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

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

    // ==================== 界面控件变量 ====================
    //
    // lateinit：延迟初始化关键字
    // 表示这些变量会在 onCreate 方法中初始化，而不是在类创建时。
    //
    // 为什么用 lateinit？
    // - 控件需要通过 findViewById 获取，而这只能在 setContentView 之后
    // - 如果不用 lateinit，就必须设置默认值或使用可空类型（?）
    //
    // private：只在这个 Activity 内部访问这些控件

    /**
     * 用户名输入框的外层容器
     *
     * TextInputLayout：Material Design 的输入框容器
     * 功能：显示标签、错误提示、辅助文本等
     */
    private lateinit var tilUsername: TextInputLayout

    /**
     * 密码输入框��外层容器
     */
    private lateinit var tilPassword: TextInputLayout

    /**
     * 确认密码输入框的外层容器（注册时显示）
     */
    private lateinit var tilPasswordConfirm: TextInputLayout

    /**
     * 用户名输入框（实际输入的地方）
     *
     * TextInputEditText：Material Design 的输入框控件
     */
    private lateinit var etUsername: TextInputEditText

    /**
     * 密码输入框（实际输入的地方）
     */
    private lateinit var etPassword: TextInputEditText

    /**
     * 确认密码输入框（注册时输入的地方）
     */
    private lateinit var etPasswordConfirm: TextInputEditText

    /**
     * 登录按钮
     *
     * MaterialButton：Material Design 的按钮控件
     */
    private lateinit var btnLogin: MaterialButton

    /**
     * 登出按钮
     */
    private lateinit var btnLogout: MaterialButton

    /**
     * 账户信息显示文本
     *
     * 用于显示已登录用户的信息
     */
    private lateinit var tvAccountInfo: TextView

    /**
     * "忘记密码"文本链接
     */
    private lateinit var tvForgotPassword: TextView

    /**
     * 模式提示文本（"没有账号？"或"已有账号？"）
     */
    private lateinit var tvModeTip: TextView

    /**
     * 模式切换文本（"立即注册"或"立即登录"）
     */
    private lateinit var tvModeSwitch: TextView

    /**
     * 当前是否为注册模式
     * true = 注册模式，false = 登录模式
     */
    private var isRegisterMode = false

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

        // 加载布局文件，将 XML 转换为界面控件
        setContentView(R.layout.activity_login)

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
     * 功能：
     * 1. 通过 findViewById 获取布局中的控件
     * 2. 设置按钮点击事件监听器
     *
     * 命名规范：
     * - til = TextInputLayout（输入框容器）
     * - et = EditText（输入框）
     * - btn = Button（按钮）
     * - tv = TextView（文本）
     */
    private fun initViews() {
        // ==================== 获取控件 ====================
        // findViewById：根据 ID 在布局文件中查找控件

        tilUsername = findViewById(R.id.tilUsername)
        tilPassword = findViewById(R.id.tilPassword)
        tilPasswordConfirm = findViewById(R.id.tilPasswordConfirm)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm)
        btnLogin = findViewById(R.id.btnLogin)
        btnLogout = findViewById(R.id.btnLogout)
        tvAccountInfo = findViewById(R.id.tvAccountInfo)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvModeTip = findViewById(R.id.tvModeTip)
        tvModeSwitch = findViewById(R.id.tvModeSwitch)

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
        btnLogin.setOnClickListener {
            // 获取输入框的文本并去除首尾空格
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (isRegisterMode) {
                // ==================== 注册模式 ====================
                val passwordConfirm = etPasswordConfirm.text.toString().trim()

                when {
                    username.isEmpty() -> {
                        tilUsername.error = "请输入账号/手机号"
                    }
                    password.isEmpty() -> {
                        tilPassword.error = "请输入密码"
                    }
                    passwordConfirm.isEmpty() -> {
                        tilPasswordConfirm.error = "请确认密码"
                    }
                    password != passwordConfirm -> {
                        tilPasswordConfirm.error = "两次密码不一致"
                    }
                    else -> {
                        // 清除错误提示
                        tilUsername.error = null
                        tilPassword.error = null
                        tilPasswordConfirm.error = null

                        // 检查用户名是否已存在
                        viewModel.checkUsernameExists(username)
                    }
                }
            } else {
                // ==================== 登录模式 ====================
                when {
                    username.isEmpty() -> {
                        tilUsername.error = "请输入账号/手机号"
                    }
                    password.isEmpty() -> {
                        tilPassword.error = "请输入密码"
                    }
                    else -> {
                        // 清除错误提示
                        tilUsername.error = null
                        tilPassword.error = null

                        // 调用 ViewModel 执行登录
                        viewModel.login(username, password)
                    }
                }
            }
        }

        /**
         * 登出按钮点击事件
         *
         * 直接调用 ViewModel 的 logout 方法
         */
        btnLogout.setOnClickListener {
            viewModel.logout()
        }

        /**
         * "忘记密码"点击事件
         *
         * Toast：Android 的轻量级提示框
         * makeText：创建 Toast
         * - this：当前 Activity 的上下文
         * - 提示文本
         * - LENGTH_SHORT：显示时长（短）
         * show()：显示 Toast
         */
        tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "忘记密码功能开发中...", Toast.LENGTH_SHORT).show()
        }

        /**
         * 模式切换点击事件（登录 ↔ 注册）
         */
        tvModeSwitch.setOnClickListener {
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
                // 显示已登录界面（显示账户信息，隐藏登录表单）
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
                // setText("")：设置文本为空字符串
                etUsername.setText("")
                etPassword.setText("")
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
         * 2. 如果成功，切换回登录模式
         */
        viewModel.registerResult.observe(this) { success ->
            if (success) {
                // 注册成功
                Toast.makeText(this, "注册成功！请登录", Toast.LENGTH_SHORT).show()

                // 清空输入框
                etUsername.setText("")
                etPassword.setText("")
                etPasswordConfirm.setText("")

                // 切换回登录模式
                isRegisterMode = false
                updateUIMode()
            } else {
                // 注册失败
                Toast.makeText(this, "注册失败，请稍后重试", Toast.LENGTH_SHORT).show()
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
                val username = etUsername.text.toString().trim()
                val password = etPassword.text.toString().trim()
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
            tilPasswordConfirm.visibility = View.VISIBLE  // 显示确认密码框
            tvForgotPassword.visibility = View.GONE       // 隐藏"忘记密码"
            btnLogin.text = "注册"                        // 按钮文字改为"注册"
            tvModeTip.text = "已有账号？"                  // 提示文字
            tvModeSwitch.text = "立即登录"                // 切换文字
        } else {
            // ==================== 登录模式 ====================
            tilPasswordConfirm.visibility = View.GONE     // 隐藏确认密码框
            tvForgotPassword.visibility = View.VISIBLE    // 显示"忘记密码"
            btnLogin.text = "登录"                        // 按钮文字改为"登录"
            tvModeTip.text = "没有账号？"                  // 提示文字
            tvModeSwitch.text = "立即注册"                // 切换文字
        }

        // 清空所有输入框和错误提示
        etUsername.setText("")
        etPassword.setText("")
        etPasswordConfirm.setText("")
        tilUsername.error = null
        tilPassword.error = null
        tilPasswordConfirm.error = null
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
        // 设置 visibility 为 GONE，让控件不可见且不占据空间

        tilUsername.visibility = View.GONE        // 隐藏用户名输入框
        tilPassword.visibility = View.GONE        // 隐藏密码输入框
        tilPasswordConfirm.visibility = View.GONE // 隐藏确认密码输入框
        tvForgotPassword.visibility = View.GONE   // 隐藏"忘记密码"
        btnLogin.visibility = View.GONE           // 隐藏登录按钮

        // findViewById<View>：临时查找控件（如果没有保存为成员变量）
        findViewById<View>(R.id.llRegister).visibility = View.GONE              // 隐藏注册区域
        findViewById<View>(R.id.llThirdPartyLogin).visibility = View.GONE       // 隐藏第三方登录区域

        // ==================== 显示账户信息 ====================

        // 显示账户信息文本
        tvAccountInfo.visibility = View.VISIBLE

        // 设置文本内容
        // $username：字符串模板，插入变量值
        // \n：换行符
        tvAccountInfo.text = "欢迎回来，$username\n账户ID: $accountId"

        // 显示登出按钮
        btnLogout.visibility = View.VISIBLE
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
        // 设置 visibility 为 VISIBLE，让控件可见

        tilUsername.visibility = View.VISIBLE        // 显示用户名输入框
        tilPassword.visibility = View.VISIBLE        // 显示密码输入框
        tvForgotPassword.visibility = View.VISIBLE   // 显示"忘记密码"
        btnLogin.visibility = View.VISIBLE           // 显示登录按钮
        findViewById<View>(R.id.llRegister).visibility = View.VISIBLE              // 显示注册区域
        findViewById<View>(R.id.llThirdPartyLogin).visibility = View.VISIBLE       // 显示第三方登录区域

        // ==================== 隐藏账户信息 ====================

        tvAccountInfo.visibility = View.GONE   // 隐藏账户信息文本
        btnLogout.visibility = View.GONE       // 隐藏登出按钮

        // ==================== 重置为登录模式 ====================
        // 确保返回登录界面时是登录模式，而不是注册模式
        isRegisterMode = false
        updateUIMode()
    }
}
