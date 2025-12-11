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
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun attachmentDao(): AttachmentDao
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
