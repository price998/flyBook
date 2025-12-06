package com.example.myapplication.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 账户数据库配置类
 *
 * 这是整个数据库的入口点，使用单例模式确保全局只有一个数据库实例。
 *
 * 什么是单例模式？
 * 单例模式确保一个类只有一个实例，并提供全局访问点。
 * 好处：避免创建多个数据库连接，节省资源，保证数据一致性。
 *
 * @Database 注解的参数说明：
 * - entities：指定这个数据库包含哪些表（我们这里只有 AccountEntity 一张表）
 * - version：数据库版本号，当修改表结构时需要增加版本号
 * - exportSchema：是否导出数据库架构文件，开发时设为 false 简化操作
 */
@Database(entities = [AccountEntity::class], version = 1, exportSchema = false)
abstract class AccountDatabase : RoomDatabase() {

    /**
     * 获取数据访问对象（DAO）
     *
     * 这是一个抽象方法，Room 会自动实现它。
     * 通过这个方法，我们可以获取 AccountDao 来操作数据库。
     *
     * @return AccountDao 实例，用于执行数据库操作
     */
    abstract fun accountDao(): AccountDao

    /**
     * companion object：Kotlin 的伴生对象
     *
     * 类似于 Java 中的 static 静态成员，
     * 可以直接通过类名调用，不需要创建对象。
     *
     * 用法：AccountDatabase.getInstance(context)
     */
    companion object {
        /**
         * 数据库实例变量
         *
         * @Volatile：确保多线程环境下的可见性
         * 当一个线程修改了 INSTANCE，其他线程能立即看到最新值。
         *
         * private：只能在这个类内部访问
         * var：可变变量，初始值为 null
         *
         * 为什么用 null？
         * 第一次使用时才创建数据库，节省资源（懒加载）
         */
        @Volatile
        private var INSTANCE: AccountDatabase? = null

        /**
         * 获取数据库实例（单例模式的核心方法）
         *
         * 功能：返回唯一的数据库实例，如果不存在则创建
         *
         * @param context Android 上下文对象，用于创建数据库
         * @return AccountDatabase 数据库实例
         *
         * 执行流程：
         * 1. 检查 INSTANCE 是否已存在
         * 2. 如果存在，直接返回
         * 3. 如果不存在，加锁创建（避免多线程重复创建）
         * 4. 创建后保存到 INSTANCE 并返回
         */
        fun getInstance(context: Context): AccountDatabase {
            // ?: 是 Elvis 运算符，意思是"如果左边为 null，就执行右边"
            return INSTANCE ?: synchronized(this) {
                // synchronized(this)：加锁，确保同一时间只有一个线程能执行这段代码
                // 这样可以防止多个线程同时创建数据库实例

                // Room.databaseBuilder：创建数据库的工具方法
                val instance = Room.databaseBuilder(
                    context.applicationContext,  // 使用应用级别的 Context，避免内存泄漏
                    AccountDatabase::class.java, // 数据库类
                    "account_database"           // 数据库文件名
                ).build()  // 构建数据库实例

                // 将创建的实例保存到 INSTANCE 变量
                INSTANCE = instance

                // 返回创建的实例
                instance
            }
        }
    }
}

