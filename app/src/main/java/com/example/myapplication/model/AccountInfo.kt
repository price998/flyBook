package com.example.myapplication.model

/**
 * 账户信息数据类（Model 层）
 *
 * 这个类用于在应用层传递账户信息，与数据库实体 AccountEntity 不同：
 *
 * AccountEntity（数据库层）：包含所有数据库字段（包括密码、登录状态等敏感信息）
 * AccountInfo（应用层）：   只包含需要展示给用户的信息（不包含密码等敏感数据）
 *
 * 为什么要分开？
 * 1. 安全性：避免在界面层传递敏感信息（如密码）
 * 2. 简洁性：界面只需要必要的信息，不需要所有数据库字段
 * 3. 解耦：数据库结构改变时，不影响界面层
 *
 * data class 的好处：
 * - 自动生成 equals()、hashCode()、toString() 方法
 * - 自动生成 copy() 方法，方便创建副本
 * - 代码简洁，易于理解
 */
data class AccountInfo(
    /**
     * 账户唯一标识符
     *
     * 与数据库中的 accountId 对应，用于唯一标识一个账户。
     *
     * 示例值："550e8400-e29b-41d4-a716-446655440000"
     */
    val accountId: String,

    /**
     * 用户名
     *
     * 用于在界面上显示的用户名称。
     *
     * 示例值："张三"、"13800138000"
     */
    val username: String
)

