package it.quadra.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringRuleEntity::class,
        ImpostazioneEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun recurringRules(): RecurringRuleDao
    abstract fun impostazioni(): ImpostazioniDao

    companion object {
        const val NAME = "quadra.db"

        /**
         * Aggiunge la tabella delle preferenze.
         *
         * Chi ha già l'app installata ha dentro le sue spese: la tabella si crea e basta,
         * niente di esistente viene toccato. `IF NOT EXISTS` rende la migrazione
         * ripetibile senza danno.
         */
        private val DA_1_A_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `impostazioni` (" +
                        "`chiave` TEXT NOT NULL, `valore` TEXT NOT NULL, " +
                        "PRIMARY KEY(`chiave`))"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                    // Nessun fallbackToDestructiveMigration: qui dentro ci sono anni di
                    // spese di qualcuno. Se un giorno lo schema cambia si scrive una
                    // migrazione, non si cancella tutto.
                    .addMigrations(DA_1_A_2)
                    .build()
                    .also { instance = it }
            }
    }
}
