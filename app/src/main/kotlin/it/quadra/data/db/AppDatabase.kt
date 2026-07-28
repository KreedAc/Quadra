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
    version = 4,
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

        /**
         * Le ricorrenti smettono di registrare da sole e diventano promemoria.
         *
         * Al posto di `autoInsert` arriva `rimandi`, che tiene le scadenze rinviate. La
         * colonna vecchia va tolta, e SQLite non sa togliere colonne fino alla 3.35 —
         * su Android 8, che è il minimo che supportiamo, siamo alla 3.19. L'unica strada
         * è ricreare la tabella e travasare i dati, che è anche il motivo per cui le
         * colonne vengono elencate una per una invece di usare `SELECT *`: così se un
         * giorno lo schema cambia ancora, questa migrazione continua a copiare
         * esattamente quello che copiava il giorno in cui è stata scritta.
         *
         * Le regole esistenti conservano tutto tranne l'automatismo, che non esiste più
         * per nessuno. I movimenti che hanno già generato restano dove sono: sono spese
         * registrate, e riscrivere il passato non è compito di una migrazione.
         */
        private val DA_2_A_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_rules_nuova` (" +
                        "`id` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                        "`amountCents` INTEGER NOT NULL, `categoryId` TEXT NOT NULL, " +
                        "`accountId` TEXT NOT NULL, `every` INTEGER NOT NULL, " +
                        "`unit` TEXT NOT NULL, `startDate` TEXT NOT NULL, " +
                        "`endDate` TEXT, `dayOfMonth` INTEGER, " +
                        "`active` INTEGER NOT NULL, `skippedDates` TEXT NOT NULL, " +
                        "`rimandi` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO `recurring_rules_nuova` (" +
                        "`id`, `description`, `amountCents`, `categoryId`, `accountId`, " +
                        "`every`, `unit`, `startDate`, `endDate`, `dayOfMonth`, " +
                        "`active`, `skippedDates`, `rimandi`) " +
                        "SELECT `id`, `description`, `amountCents`, `categoryId`, `accountId`, " +
                        "`every`, `unit`, `startDate`, `endDate`, `dayOfMonth`, " +
                        "`active`, `skippedDates`, '' FROM `recurring_rules`"
                )
                db.execSQL("DROP TABLE `recurring_rules`")
                db.execSQL("ALTER TABLE `recurring_rules_nuova` RENAME TO `recurring_rules`")
            }
        }

        /**
         * Le regole ricordano quando sono nate.
         *
         * Serve a non chiedere conferma per scadenze anteriori al promemoria stesso.
         * Alle regole già esistenti si assegna il giorno della migrazione: quelle
         * scadenze o sono già state confermate, o sono state saltate, o riguardano un
         * periodo in cui l'app non teneva ancora il conto — in nessuno dei tre casi ha
         * senso ripescarle adesso.
         */
        private val DA_3_A_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `recurring_rules` ADD COLUMN `creatoIl` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL("UPDATE `recurring_rules` SET `creatoIl` = date('now')")
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
                    .addMigrations(DA_1_A_2, DA_2_A_3, DA_3_A_4)
                    .build()
                    .also { instance = it }
            }
    }
}
