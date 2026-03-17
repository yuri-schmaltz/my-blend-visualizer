package com.myblendvisualizer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversion_history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val modelUrl: String,
    val sizeBytes: Long?,
    val timestamp: Long = System.currentTimeMillis()
)
