package com.study.jeestudytimer

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "study_history")
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTimeFormatted: String,
    val durationMillis: Long,
    val type: String,
    val totalBreaksCount: Int,
    val breakLogs: String
)

@Dao
interface StudyDao {
    @Query("SELECT * FROM study_history ORDER BY id DESC")
    fun getAllSessions(): Flow<List<StudySession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySession)
}

@Database(entities = [StudySession::class], version = 1, exportSchema = false)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        @Volatile
        private var INSTANCE: StudyDatabase? = null

        fun getDatabase(context: Context): StudyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudyDatabase::class.java,
                    "study_timer_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
