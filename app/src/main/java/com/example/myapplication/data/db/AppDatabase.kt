package com.example.myapplication.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.myapplication.data.db.account.AccountDao
import com.example.myapplication.data.db.account.AccountEntity
import com.example.myapplication.data.db.chat.AttachmentDao
import com.example.myapplication.data.db.chat.AttachmentEntity
import com.example.myapplication.data.db.chat.ConversationDao
import com.example.myapplication.data.db.chat.ConversationEntity
import com.example.myapplication.data.db.chat.MessageDao
import com.example.myapplication.data.db.chat.MessageEntity
import com.example.myapplication.data.db.migrations.DatabaseMigrations

/**
 * 应用主数据库（Room Database）
 * 
 * 这是应用的核心数据库，使用 Room 框架管理本地数据持久化。
 * 
 * 数据库结构：
 * - MessageEntity：消息表（存储聊天消息）
 * - ConversationEntity：对话表（存储对话元数据）
 * - AttachmentEntity：附件表（存储消息附件）
 * - AccountEntity：账户表（存储用户账户信息）
 * 
 * 版本管理：
 * - 当前版本：3
 * - exportSchema = true：导出数据库 schema 到 app/schemas/ 目录，便于版本管理和迁移
 * 
 * 数据库迁移：
 * - 所有迁移策略定义在 DatabaseMigrations 对象中
 * - 使用 addMigrations() 注册迁移，确保用户数据不丢失
 * - 已移除 fallbackToDestructiveMigration()，保证数据安全
 * 
 * 单例模式：
 * - 使用 companion object + @Volatile 实现线程安全的单例
 * - 通过 getDatabase(context) 获取数据库实例
 */
@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        AttachmentEntity::class,
        AccountEntity::class
    ],
    version = 3,
    exportSchema = true // 导出 schema 便于管理迁移
)
abstract class AppDatabase : RoomDatabase() {
    /** 消息数据访问对象 */
    abstract fun messageDao(): MessageDao
    
    /** 对话数据访问对象 */
    abstract fun conversationDao(): ConversationDao
    
    /** 附件数据访问对象 */
    abstract fun attachmentDao(): AttachmentDao
    
    /** 账户数据访问对象 */
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * 获取数据库实例
         * 
         * 数据库迁移策略：
         * - 所有迁移定义在 DatabaseMigrations 对象中
         * - 使用 addMigrations() 注册所有迁移，保护用户数据
         * - 已移除 fallbackToDestructiveMigration()，确保数据安全
         * 
         * 如何添加新迁移：
         * 1. 修改 Entity 类（如添加字段）
         * 2. 增加 @Database 的 version 号
         * 3. 在 DatabaseMigrations 中添加新的 MIGRATION_X_Y
         * 4. 将新迁移添加到 DatabaseMigrations.ALL_MIGRATIONS 数组
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chat_database"
                )
                    // 注册所有迁移策略，保护用户数据不丢失
                    .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                    
                    // ✅ 已移除 fallbackToDestructiveMigration()
                    // 现在所有数据库升级都必须通过迁移完成，确保用户数据安全
                    
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
