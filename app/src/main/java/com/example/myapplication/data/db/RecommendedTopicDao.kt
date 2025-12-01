package com.example.myapplication.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendedTopicDao {
    @Query("SELECT * FROM recommended_topics ORDER BY id DESC")
    fun getAllTopics(): Flow<List<RecommendedTopicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(topics: List<RecommendedTopicEntity>)

    @Query("SELECT COUNT(*) FROM recommended_topics")
    suspend fun getCount(): Int
}
