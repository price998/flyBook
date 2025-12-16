# 数据库迁移指南

## 📋 概述

本项目使用 Room 数据库，并实现了完整的数据库迁移策略，确保用户数据在应用升级时不会丢失。

## 🎯 核心原则

1. **永远不要删除已有的迁移** - 用户可能从任何旧版本升级
2. **每次修改数据库结构必须增加版本号** - 在 `@Database` 注解中
3. **所有迁移必须保证数据不丢失** - 使用 SQL 语句正确迁移数据
4. **测试所有可能的升级路径** - 1→2, 1→3, 2→3 等

## 📚 当前迁移历史

### Version 1 → 2
**变更内容：** 移除 `conversations` 表的 `deletedAt` 字段

**原因：** 简化软删除逻辑，使用 `isDeleted` 字段即可

**实现方式：**
- 创建新表（不包含 `deletedAt`）
- 复制所有数据
- 删除旧表
- 重命名新表

**代码位置：** `DatabaseMigrations.MIGRATION_1_2`

## 🔧 如何添加新迁移

### 步骤 1: 修改 Entity 类

例如，为 `MessageEntity` 添加新字段：

```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    // ... 现有字段 ...
    val newField: String = ""  // 新增字段
)
```

### 步骤 2: 增加数据库版本号

在 `AppDatabase.kt` 中：

```kotlin
@Database(
    entities = [...],
    version = 3,  // 从 2 增加到 3
    exportSchema = true
)
```

### 步骤 3: 创建迁移

在 `DatabaseMigrations.kt` 中添加：

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 添加新字段（提供默认值）
        db.execSQL("ALTER TABLE messages ADD COLUMN newField TEXT NOT NULL DEFAULT ''")
    }
}
```

### 步骤 4: 注册迁移

在 `DatabaseMigrations.kt` 的 `ALL_MIGRATIONS` 中添加：

```kotlin
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3  // 新增
)
```

## 📝 常见迁移场景

### 1. 添加新字段

```kotlin
db.execSQL("ALTER TABLE table_name ADD COLUMN column_name TEXT DEFAULT ''")
```

### 2. 删除字段（SQLite 不支持直接删除）

```kotlin
// 1. 创建新表
db.execSQL("CREATE TABLE table_new (...)")

// 2. 复制数据（排除要删除的字段）
db.execSQL("INSERT INTO table_new SELECT col1, col2 FROM table_old")

// 3. 删除旧表
db.execSQL("DROP TABLE table_old")

// 4. 重命名
db.execSQL("ALTER TABLE table_new RENAME TO table_old")
```

### 3. 修改字段类型

```kotlin
// 同删除字段的方式，重建表
```

### 4. 添加新表

```kotlin
db.execSQL("""
    CREATE TABLE IF NOT EXISTS new_table (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        name TEXT NOT NULL
    )
""")
```

### 5. 添加索引

```kotlin
db.execSQL("CREATE INDEX IF NOT EXISTS index_name ON table_name(column_name)")
```

### 6. 添加外键约束

```kotlin
// 需要重建表，因为 SQLite 不支持直接添加外键
```

## ✅ 测试迁移

### 单元测试示例

```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2() {
        // 创建版本 1 的数据库
        helper.createDatabase(TEST_DB, 1).apply {
            // 插入测试数据
            execSQL("INSERT INTO conversations VALUES (...)")
            close()
        }

        // 执行迁移到版本 2
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // 验证数据完整性
        helper.getMigrationDatabase().query("SELECT * FROM conversations").use { cursor ->
            assertTrue(cursor.moveToFirst())
            // 验证数据...
        }
    }
}
```

## 🚨 注意事项

### ❌ 不要做的事

1. **不要使用 `fallbackToDestructiveMigration()`** - 会清空用户数据
2. **不要删除已有的迁移** - 用户可能从旧版本升级
3. **不要跳过版本号** - 必须连续：1→2→3→4
4. **不要在生产环境修改已发布的迁移** - 会导致升级失败

### ✅ 应该做的事

1. **总是提供完整的迁移路径** - 从任何旧版本到最新版本
2. **总是测试迁移** - 使用 MigrationTestHelper
3. **总是保留默认值** - 添加新字段时提供 DEFAULT
4. **总是导出 schema** - `exportSchema = true`
5. **总是备份数据** - 在重建表之前

## 📊 迁移路径示例

```
Version 1 ──MIGRATION_1_2──> Version 2 ──MIGRATION_2_3──> Version 3
    │                                                          │
    └──────────────────MIGRATION_1_3──────────────────────────┘
```

如果用户从版本 1 直接升级到版本 3，Room 会自动执行：
- MIGRATION_1_2
- MIGRATION_2_3

或者如果提供了直接路径：
- MIGRATION_1_3

## 🔍 调试迁移

### 启用 Room 日志

```kotlin
Room.databaseBuilder(...)
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    .build()
```

### 查看 Schema 文件

导出的 schema 文件位于：
```
app/schemas/com.example.myapplication.data.db.AppDatabase/
├── 1.json
├── 2.json
└── 3.json
```

使用这些文件对比版本间的差异。

## 📖 参考资料

- [Room Migration 官方文档](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [SQLite ALTER TABLE 文档](https://www.sqlite.org/lang_altertable.html)
- [Room Testing 文档](https://developer.android.com/training/data-storage/room/testing-db)

## 🎓 最佳实践

1. **版本规划** - 在开发新功能前规划好数据库变更
2. **向后兼容** - 尽量使用 ALTER TABLE ADD COLUMN 而不是重建表
3. **数据验证** - 迁移后验证数据完整性
4. **性能考虑** - 大数据量迁移时考虑分批处理
5. **文档记录** - 每次迁移都要记录原因和变更内容
