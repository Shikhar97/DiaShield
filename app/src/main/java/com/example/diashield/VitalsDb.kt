package com.example.diashield


import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class VitalsDb {
    @Entity
    data class VitalsUser(
        @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "uid") val uid: Long = 0L,
        @ColumnInfo(name = "heart_rate") val heartRate: Float?,
        @ColumnInfo(name = "resp_rate") val respRate: Float?,
        @ColumnInfo(name = "nausea") val nausea: Float?,
        @ColumnInfo(name = "headache") val headache: Float?,
        @ColumnInfo(name = "diarrhea") val diarrhea: Float?,
        @ColumnInfo(name = "soar_throat") val soarThroat: Float?,
        @ColumnInfo(name = "fever") val fever: Float?,
        @ColumnInfo(name = "muscle_ache") val muscleAche: Float?,
        @ColumnInfo(name = "loss_of_smell_taste") val lossOfSmellTaste: Float?,
        @ColumnInfo(name = "cough") val cough: Float?,
        @ColumnInfo(name = "breathlessness") val breathlessness: Float?,
        @ColumnInfo(name = "feeling_tired") val feelingTired: Float?

    )

    @Dao
    interface VitalsDao {
        @Query("SELECT * FROM VitalsUser")
        fun getAll(): Flow<List<VitalsUser>>

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insert(vararg users: VitalsUser)

        @Query("DELETE FROM VitalsUser")
        suspend fun deleteAll()
    }

    @Database(entities = [VitalsUser::class], version = 1, exportSchema = false)
    abstract class AppDatabase : RoomDatabase() {
        abstract fun userDao(): VitalsDao

        private class AppDatabaseCallback(
            private val scope: CoroutineScope
        ) : Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch {
                        val wordDao = database.userDao()
                        // Delete all content here.
                        wordDao.deleteAll()
                    }
                }
            }
        }

        companion object {
            // Singleton prevents multiple instances of database opening at the
            // same time.
            @Volatile
            private var INSTANCE: AppDatabase? = null

            fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
                // if the INSTANCE is not null, then return it,
                // if it is, then create the database
                return INSTANCE ?: synchronized(this) {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "health.db"
                    )
                        .addCallback(AppDatabaseCallback(scope))
                        .build()
                    INSTANCE = instance
                    // return instance
                    instance
                }
            }
        }
    }
}



