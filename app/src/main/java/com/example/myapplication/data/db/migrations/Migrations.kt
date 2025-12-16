package com.example.myapplication.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移策略集合
 * 
 * 重要原则：
 * 1. 永远不要删除已有的迁移
 * 2. 每次修改数据库结构时，必须增加版本号并添加新的迁移
 * 3. 迁移必须保证数据不丢失
 * 4. 测试迁移路径：1->2, 1->3, 2->3 等所有可能的升级路径
 */
object DatabaseMigrations {
    
    /**
     * 版本 1 -> 2: 移除 conversations 表的 deletedAt 字段
     * 
     * 变更说明：
     * - 删除了 conversations.deletedAt 字段
     * - 保留所有现有数据
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // SQLite 不支持直接删除列，需要通过重建表的方式
            // 步骤：创建新表 -> 复制数据 -> 删除旧表 -> 重命名新表
            
            // 1. 创建新的 conversations 表（不包含 deletedAt 字段）
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversations_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    messageCount INTEGER NOT NULL,
                    isPinned INTEGER NOT NULL,
                    isDeleted INTEGER NOT NULL,
                    lastMessagePreview TEXT,
                    userId TEXT NOT NULL
                )
            """.trimIndent())
            
            // 2. 复制数据（排除 deletedAt 字段）
            db.execSQL("""
                INSERT INTO conversations_new (
                    id, title, createdAt, updatedAt, messageCount,
                    isPinned, isDeleted, lastMessagePreview, userId
                )
                SELECT 
                    id, title, createdAt, updatedAt, messageCount,
                    isPinned, isDeleted, lastMessagePreview, userId
                FROM conversations
            """.trimIndent())
            
            // 3. 删除旧表
            db.execSQL("DROP TABLE conversations")
            
            // 4. 重命名新表
            db.execSQL("ALTER TABLE conversations_new RENAME TO conversations")
        }
    }
    
    /**
     * 未来迁移示例：版本 2 -> 3
     * 
     * 当需要添加新功能时，在这里添加新的迁移
     * 例如：添加消息搜索功能，需要全文索引
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 示例：为 messages 表添加全文搜索支持
            // db.execSQL("""
            //     CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts 
            //     USING fts4(content=messages, content TEXT)
            // """.trimIndent())
            
            // 示例：添加新字段
            // db.execSQL("ALTER TABLE messages ADD COLUMN searchable_content TEXT DEFAULT ''")
        }
    }
    
    /**
     * 未来迁移示例：版本 3 -> 4
     * 
     * 例如：添加消息标签功能
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 示例：创建标签表
            // db.execSQL("""
            //     CREATE TABLE IF NOT EXISTS tags (
            //         id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            //         name TEXT NOT NULL,
            //         color TEXT NOT NULL,
            //         createdAt INTEGER NOT NULL
            //     )
            // """.trimIndent())
            
            // 示例：创建消息-标签关联表
            // db.execSQL("""
            //     CREATE TABLE IF NOT EXISTS message_tags (
            //         messageId INTEGER NOT NULL,
            //         tagId INTEGER NOT NULL,
            //         PRIMARY KEY(messageId, tagId),
            //         FOREIGN KEY(messageId) REFERENCES messages(id) ON DELETE CASCADE,
            //         FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE
            //     )
            // """.trimIndent())
        }
    }
    
    /**
     * 获取所有迁移的数组
     * 
     * 使用方式：
     * Room.databaseBuilder(...)
     *     .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
     *     .build()
     */
    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2
        // 未来添加新迁移时，在这里注册：
        // MIGRATION_1_2,
        // MIGRATION_2_3,
        // MIGRATION_3_4
    )
}
