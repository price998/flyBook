package com.example.myapplication.repository

import android.content.Context
import com.example.myapplication.data.db.AccountDatabase
import com.example.myapplication.data.db.AccountEntity
import com.example.myapplication.model.AccountInfo
import java.util.UUID

/**
 * 账户数据仓库（Repository 层）
 *
 * 什么是 Repository？
 * Repository 是数据仓库，它是数据层和业务逻辑层之间的桥梁。
 *
 * MVVM 架构中的位置：
 * View（界面） → ViewModel（业务逻辑） → Repository（数据仓库） → Database（数据库）
 *
 * Repository 的职责：
 * 1. 封装数据操作：ViewModel 不直接访问数据库，而是通过 Repository
 * 2. 数据转换：将数据库实体（AccountEntity）转换为应用模型（AccountInfo）
 * 3. 异常处理：捕获数据库操作异常，避免应用崩溃
 * 4. 业务逻辑：处理数据相关的业务规则（如登录验证、账户创建等）
 *
 * 好处：
 * - 解耦：ViewModel 不需要知道数据来自数据库还是网络
 * - 复用：多个 ViewModel 可以共用同一个 Repository
 * - 测试：可以轻松创建假的 Repository 进行单元测试
 */
class AccountRepository(context: Context) {

    /**
     * 数据访问对象（DAO）
     *
     * 通过 AccountDatabase 获取 DAO 实例，用于执行数据库操作。
     * private：只在这个类内部使用，外部无法直接访问数据库
     */
    private val accountDao = AccountDatabase.getInstance(context).accountDao()

    /**
     * 判断当前是否已登录
     *
     * 功能：检查是否有账户处于登录状态
     *
     * @return true=已登录，false=未登录
     *
     * 实现逻辑：
     * 1. 查询数据库，获取标记为"已登录"的账户
     * 2. 如果找到账户，返回 true
     * 3. 如果没找到，返回 false
     * 4. 如果出现异常（如数据库错误），返回 false
     *
     * try-catch 的作用：
     * - try 块：尝试执行可能出错的代码
     * - catch 块：如果出错，执行这里的代码
     * - _: Exception：下划线表示我们不使用异常对象，捕获所有异常
     */
    fun isLoggedIn(): Boolean {
        return try {
            // != null 判断：如果找到账户（不为空），返回 true
            accountDao.getLoggedInAccount() != null
        } catch (_: Exception) {
            // 发生异常时，认为未登录
            false
        }
    }

    /**
     * 获取当前登录账户信息
     *
     * 功能：获取已登录账户的信息（仅包含非敏感数据）
     *
     * @return AccountInfo 账户信息对象，如果未登录则返回 null
     *
     * 数据转换过程：
     * AccountEntity（数据库） → AccountInfo（应用层）
     *
     * 为什么要转换？
     * - 数据库实体包含密码等敏感信息，不应该传递到界面层
     * - 应用层只需要用户名和ID，不需要其他字段
     *
     * let 函数的作用：
     * entity?.let { ... } 表示"如果 entity 不为 null，就执行大括号里的代码"
     * it 代表 entity 对象
     */
    fun getAccount(): AccountInfo? {
        return try {
            // 从数据库获取已登录的账户实体
            val entity = accountDao.getLoggedInAccount()

            // 将数据库实体转换为应用层模型（只包含必要信息）
            entity?.let { AccountInfo(it.accountId, it.username) }
        } catch (_: Exception) {
            // 发生异常时返回 null
            null
        }
    }

    /**
     * 登录方法
     *
     * 功能：验证用户名和密码，如果正确则登录
     *
     * @param username 用户名
     * @param password 密码
     * @return true=登录成功，false=登录失败（账号不存在或密码错误）
     *
     * 执行流程：
     * 1. 先清除所有登录状态（确保只有一个账户登录）
     * 2. 根据用户名查找账户是否存在
     * 3. 如果账户不存在：返回 false
     * 4. 如果账户存在：
     *    - 验证密码是否正确
     *    - 密码正确：标记为已登录，返回 true
     *    - 密码错误：返回 false
     * 5. 如果发生异常：返回 false
     */
    fun login(username: String, password: String): Boolean {
        return try {
            // 步骤1：先清除其它登录态（确保单一登录）
            accountDao.logoutAll()

            // 步骤2：查找该用户名是否已存在
            val existingAccount = accountDao.findByUsername(username)

            if (existingAccount != null) {
                // 步骤3：账号已存在，必须验证密码
                if (existingAccount.password == password) {
                    // 密码正确，标记为已登录
                    accountDao.markAsLoggedIn(existingAccount.accountId)
                    true
                } else {
                    // 密码错误，登录失败
                    false
                }
            } else {
                // 步骤4：账号不存在，登录失败
                false
            }
        } catch (_: Exception) {
            // 发生异常，登录失败
            false
        }
    }

    /**
     * 注册方法
     *
     * 功能：创建新账户
     *
     * @param username 用户名
     * @param password 密码
     * @return true=注册成功，false=注册失败（账号已存在）
     *
     * 执行流程：
     * 1. 检查用户名是否已存在
     * 2. 如果已存在：返回 false（账号已被使用）
     * 3. 如果不存在：创建新账户并保存到数据库，返回 true
     * 4. 如果发生异常：返回 false
     */
    fun register(username: String, password: String): Boolean {
        return try {
            // 步骤1：检查用户名是否已存在
            val existingAccount = accountDao.findByUsername(username)

            if (existingAccount != null) {
                // 账号已存在，注册失败
                false
            } else {
                // 步骤2：创建新账户
                val newAccount = AccountEntity(
                    accountId = UUID.randomUUID().toString(),
                    username = username,
                    password = password,
                    isLoggedIn = 0  // 注册后不自动登录
                )
                accountDao.insertAccount(newAccount)
                true
            }
        } catch (_: Exception) {
            // 发生异常，注册失败
            false
        }
    }

    /**
     * 检查用户名是否已存在
     *
     * 功能：查询用户名是否已被使用
     *
     * @param username 用户名
     * @return true=已存在，false=不存在
     */
    fun isUsernameExists(username: String): Boolean {
        return try {
            accountDao.findByUsername(username) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 登出方法
     *
     * 功能：将当前登录的账户设为未登录状态
     *
     * @return true=登出成功，false=登出失败
     *
     * 实现逻辑：
     * 1. 调用 DAO 的 logoutAll() 方法
     * 2. 该方法会将所有已登录账户标记为未登录
     * 3. 如果操作成功，返回 true
     * 4. 如果发生异常，返回 false
     */
    fun logout(): Boolean {
        return try {
            // 将所有账户设为未登录状态
            accountDao.logoutAll()
            true
        } catch (_: Exception) {
            // 发生异常，登出失败
            false
        }
    }

    /**
     * 更新账户信息
     *
     * 功能：修改当前登录账户的用户名或密码
     *
     * @param username 新的用户名（如果为 null 则不修改）
     * @param password 新的密码（如果为 null 则不修改）
     * @return true=更新成功，false=更新失败
     *
     * 实现逻辑：
     * 1. 获取当前登录的账户
     * 2. 如果没有登录账户，直接返回 false
     * 3. 使用 copy() 方法创建更新后的账户对象
     * 4. 保存到数据库
     * 5. 返回 true
     *
     * copy() 方法：
     * data class 自动生成的方法，用于创建副本并修改部分字段
     *
     * ?: 运算符（Elvis 运算符）：
     * username ?: current.username 表示"如果 username 不为 null，用新值，否则用旧值"
     */
    fun updateAccount(username: String?, password: String?): Boolean {
        return try {
            // 获取当前登录的账户，如果为 null 则直接返回 false
            val current = accountDao.getLoggedInAccount() ?: return false

            // 创建更新后的账户对象
            val updated = current.copy(
                username = username ?: current.username,  // 如果新用户名为 null，保持原值
                password = password ?: current.password   // 如果新密码为 null，保持原值
            )

            // 保存到数据库
            accountDao.updateAccount(updated)
            true
        } catch (_: Exception) {
            // 发生异常，更新失败
            false
        }
    }
}

