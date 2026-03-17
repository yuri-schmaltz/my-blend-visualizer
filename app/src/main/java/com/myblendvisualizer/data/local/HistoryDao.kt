package com.myblendvisualizer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM conversion_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: HistoryEntry)

    @Query("DELETE FROM conversion_history WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM conversion_history")
    suspend fun clearHistory()
}
