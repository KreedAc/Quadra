package it.quadra.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringRuleEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun recurringRules(): RecurringRuleDao

    companion object {
        const val NAME = "quadra.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                    // Nessun fallbackToDestructiveMigration: qui dentro ci sono anni di
                    // spese di qualcuno. Se un giorno lo schema cambia si scrive una
                    // migrazione, non si cancella tutto.
                    .build()
                    .also { instance = it }
            }
    }
}
