package com.example.myapplication.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendedTopicDao {
    @Query("SELECT * FROM recommended_topics WHERE isActive = 1 ORDER BY sortOrder ASC, id DESC")
    fun getAllActiveTopics(): Flow<List<RecommendedTopicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(topics: List<RecommendedTopicEntity>)

    @Query("UPDATE recommended_topics SET clickCount = clickCount + 1 WHERE id = :id")
    suspend fun incrementClickCount(id: Long)
    
    @Query("SELECT COUNT(*) FROM recommended_topics")
    suspend fun getCount(): Int
}
