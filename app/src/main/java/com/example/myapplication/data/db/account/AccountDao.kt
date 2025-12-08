package com.example.myapplication.data.db.account

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * 账户数据访问对象（DAO - Data Access Object）
 *
 * 这是一个接口，定义了访问数据库的所有操作方法。
 * Room 框架会自动为这个接口生成实现代码，我们不需要手动编写。
 *
 * @Dao 注解告诉 Room 这是一个数据访问接口
 */
@Dao
interface AccountDao {

    /**
     * 根据用户名查找账户
     *
     * 功能：只根据用户名查找账户，不验证密码
     *
     * @param username 用户名
     * @return 如果找到该用户名的账户返回 AccountEntity 对象，否则返回 null
     *
     * 用途：检查账号是否已存在（用于区分注册和登录）
     */
    @Query("SELECT * FROM accounts WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): AccountEntity?

    /**
     * 登录验证方法
     *
     * 功能：根据用户名和密码查找账户
     *
     * @param username 用户名
     * @param password 密码
     * @return 如果找到匹配的账户返回 AccountEntity 对象，否则返回 null
     *
     * @Query 注解：定义 SQL 查询语句
     * :username 和 :password 是参数占位符，会被方法参数替换
     * LIMIT 1 表示只返回一条记录
     */
    @Query("SELECT * FROM accounts WHERE username = :username AND password = :password LIMIT 1")
    suspend fun login(username: String, password: String): AccountEntity?

    /**
     * 获取当前登录的账户
     *
     * 功能：查找标记为"已登录"状态的账户
     *
     * @return 返回已登录的账户，如果没有则返回 null
     *
     * isLoggedIn = 1 表示该账户处于登录状态
     */
    @Query("SELECT * FROM accounts WHERE isLoggedIn = 1 LIMIT 1")
    suspend fun getLoggedInAccount(): AccountEntity?

    /**
     * 登出所有账户
     *
     * 功能：将所有处于登录状态的账户标记为未登录
     *
     * 这个方法没有返回值，只是执行更新操作
     * SET isLoggedIn = 0 将登录标志设为 0（未登录）
     */
    @Query("UPDATE accounts SET isLoggedIn = 0 WHERE isLoggedIn = 1")
    suspend fun logoutAll()

    /**
     * 更新账户信息
     *
     * 功能：修改现有账户的数据
     *
     * @param account 要更新的账户对象
     *
     * @Update 注解告诉 Room 这是一个更新操作
     * Room 会根据主键（accountId）找到对应的记录并更新
     */
    @Update
    suspend fun updateAccount(account: AccountEntity)

    /**
     * 插入新账户
     *
     * 功能：向数据库添加一个新的账户记录
     *
     * @param account 要插入的账户对象
     *
     * @Insert 注解告诉 Room 这是一个插入操作
     */
    @Insert
    suspend fun insertAccount(account: AccountEntity)

    /**
     * 标记账户为已登录状态
     *
     * 功能：将指定 ID 的账户设置为登录状态
     *
     * @param accountId 要标记的账户 ID
     *
     * SET isLoggedIn = 1 将登录标志设为 1（已登录）
     */
    @Query("UPDATE accounts SET isLoggedIn = 1 WHERE accountId = :accountId")
    suspend fun markAsLoggedIn(accountId: String)

}
