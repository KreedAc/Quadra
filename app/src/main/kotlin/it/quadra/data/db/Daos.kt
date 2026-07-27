package it.quadra.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(accounts: List<AccountEntity>)

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(categories: List<CategoryEntity>)

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Collection<String>)
}

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** Estremi inclusi. Le date sono ISO, quindi il confronto fra stringhe è corretto. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE date BETWEEN :from AND :to
        ORDER BY date DESC, createdAtEpochMillis DESC
        """
    )
    fun observeBetween(from: String, to: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC")
    fun observeForAccount(accountId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
    suspend fun legsOfTransfer(groupId: String): List<TransactionEntity>

    @Query("SELECT date FROM transactions WHERE recurringRuleId = :ruleId")
    suspend fun datesGeneratedBy(ruleId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transactions: List<TransactionEntity>)

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Upsert
    suspend fun upsert(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Collection<String>)

    /**
     * Cancella insieme tutte le gambe di un trasferimento.
     *
     * È annotata come transazione di database perché cancellarne una sola lascerebbe
     * l'altra orfana, e per un istante i conti non tornerebbero.
     */
    @Transaction
    suspend fun deleteWithLegs(transaction: TransactionEntity) {
        val ids = transaction.transferGroupId
            ?.let { group -> legsOfTransfer(group).map { it.id } }
            ?: listOf(transaction.id)
        deleteByIds(ids)
    }
}

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules ORDER BY description")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE active = 1")
    suspend fun activeRules(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun byId(id: String): RecurringRuleEntity?

    @Upsert
    suspend fun upsert(rule: RecurringRuleEntity)

    @Delete
    suspend fun delete(rule: RecurringRuleEntity)
}
