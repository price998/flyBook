package com.example.myapplication.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.data.db.account.AccountDao
import com.example.myapplication.data.db.account.AccountEntity
import com.example.myapplication.data.db.chat.AttachmentDao
import com.example.myapplication.data.db.chat.AttachmentEntity
import com.example.myapplication.data.db.chat.ConversationDao
import com.example.myapplication.data.db.chat.ConversationEntity
import com.example.myapplication.data.db.chat.MessageDao
import com.example.myapplication.data.db.chat.MessageEntity

@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        AttachmentEntity::class,
        AccountEntity::class
    ],
    version = 2,
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
         * 数据库迁移策略
         * 
         * 重要：每次修改数据库结构时，必须：
         * 1. 增加 version 号
         * 2. 添加对应的 Migration
         * 3. 在 getDatabase() 中注册 Migration
         * 
         * 示例：从版本 1 迁移到版本 2
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 示例：添加新字段
                // database.execSQL("ALTER TABLE messages ADD COLUMN new_field TEXT")
                
                // 当前版本 2 已经是初始版本，无需迁移
                // 如果未来需要从版本 2 升级，在这里添加 SQL 语句
            }
        }

        /**
         * 未来的迁移示例（当需要升级到版本 3 时）
         * 
         * private val MIGRATION_2_3 = object : Migration(2, 3) {
         *     override fun migrate(database: SupportSQLiteDatabase) {
         *         // 添加新表
         *         database.execSQL("""
         *             CREATE TABLE IF NOT EXISTS new_table (
         *                 id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
         *                 name TEXT NOT NULL
         *             )
         *         """.trimIndent())
         *         
         *         // 或修改现有表
         *         database.execSQL("ALTER TABLE messages ADD COLUMN new_column TEXT DEFAULT ''")
         *     }
         * }
         */

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chat_database"
                )
                    // 注册所有迁移策略
                    .addMigrations(MIGRATION_1_2)
                    // 未来添加新迁移时，在这里注册：
                    // .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    
                    // ⚠️ 仅在开发阶段使用，生产环境必须移除！
                    // .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
