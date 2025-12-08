package com.example.myapplication.data.db.account

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 账户数据库实体类（Entity）
 *
 * 这个类代表数据库中的一张表，每个对象对应表中的一行数据。
 *
 * @Entity 注解：告诉 Room 这是一个数据库表
 * tableName = "accounts"：指定表名为 "accounts"
 *
 * data class：Kotlin 的数据类，自动生成 equals、hashCode、toString 等方法
 *
 * 表结构说明：
 * ┌─────────────┬──────────┬──────────┬────────────┐
 * │ accountId   │ username │ password │ isLoggedIn │
 * │ (主键)      │          │          │            │
 * ├─────────────┼──────────┼──────────┼────────────┤
 * │ "uuid-123"  │ "张三"   │ "123456" │ 1          │
 * │ "uuid-456"  │ "李四"   │ "abcdef" │ 0          │
 * └─────────────┴──────────┴──────────┴────────────┘
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    /**
     * 账户唯一标识符（主键）
     *
     * @PrimaryKey：标记这是表的主键，每条记录必须唯一
     * 主键的作用：用于唯一标识每一条账户记录
     *
     * 示例值："550e8400-e29b-41d4-a716-446655440000"（UUID格式）
     */
    @PrimaryKey
    val accountId: String,

    /**
     * 用户名
     *
     * 存储用户的登录账号名称
     * 示例值："zhangsan"、"13800138000"（可以是用户名或手机号）
     */
    val username: String,

    /**
     * 密码
     *
     * 存储用户的登录密码
     * 注意：实际项目中应该存储加密后的密码，这里为了演示简化处理
     * 示例值："123456"
     */
    val password: String,

    /**
     * 登录状态标志
     *
     * 用于标记账户是否处于登录状态
     * 值说明：
     *   1 = 已登录（用户当前在线）
     *   0 = 未登录（默认值，用户已登出或从未登录）
     *
     * Int 类型：Room 数据库中常用整数表示布尔值
     * = 0：这是默认值，如果创建账户时不指定，默认为未登录状态
     */
    val isLoggedIn: Int = 0
)
