package it.lemiespese.data

import it.lemiespese.core.model.Account
import it.lemiespese.core.model.AccountKind
import it.lemiespese.core.model.Category
import it.lemiespese.core.model.CategoryKind
import it.lemiespese.core.model.Money
import it.lemiespese.core.model.RecurrenceUnit
import it.lemiespese.core.model.RecurringRule
import it.lemiespese.core.model.Transaction
import it.lemiespese.core.model.TransactionSource
import it.lemiespese.core.model.TransactionStatus
import it.lemiespese.data.db.AccountEntity
import it.lemiespese.data.db.CategoryEntity
import it.lemiespese.data.db.RecurringRuleEntity
import it.lemiespese.data.db.TransactionEntity
import java.time.Instant
import java.time.LocalDate

/**
 * Traduzione fra tabelle e modello di dominio.
 *
 * Gli enum si salvano per nome e non per posizione: salvare l'ordinale significa che
 * riordinare o inserire un valore nell'enum corrompe silenziosamente tutti i dati già
 * scritti. Un nome sconosciuto in lettura ricade su un valore di riserva invece di far
 * saltare l'app, perché un database più recente dell'app succede dopo un ripristino.
 */

// ───────────────────────────────────────────────────────────────── conti

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    kind = enumOrDefault(kind, AccountKind.OTHER),
    colorArgb = colorArgb,
    openingBalance = Money(openingBalanceCents),
    includedInTotal = includedInTotal,
    icon = icon,
    sortOrder = sortOrder,
    archived = archived,
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    kind = kind.name,
    colorArgb = colorArgb,
    openingBalanceCents = openingBalance.cents,
    includedInTotal = includedInTotal,
    icon = icon,
    sortOrder = sortOrder,
    archived = archived,
)

// ────────────────────────────────────────────────────────────── categorie

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    kind = enumOrDefault(kind, CategoryKind.EXPENSE),
    parentId = parentId,
    colorArgb = colorArgb,
    icon = icon,
    sortOrder = sortOrder,
    isSystem = isSystem,
    hidden = hidden,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    kind = kind.name,
    parentId = parentId,
    colorArgb = colorArgb,
    icon = icon,
    sortOrder = sortOrder,
    isSystem = isSystem,
    hidden = hidden,
)

// ─────────────────────────────────────────────────────────────── movimenti

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amount = Money(amountCents),
    date = LocalDate.parse(date),
    categoryId = categoryId,
    accountId = accountId,
    transferGroupId = transferGroupId,
    description = description,
    merchant = merchant,
    notes = notes,
    source = enumOrDefault(source, TransactionSource.MANUAL),
    status = enumOrDefault(status, TransactionStatus.SETTLED),
    recurringRuleId = recurringRuleId,
    externalKey = externalKey,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    amountCents = amount.cents,
    date = date.toString(),
    categoryId = categoryId,
    accountId = accountId,
    transferGroupId = transferGroupId,
    description = description,
    merchant = merchant,
    notes = notes,
    source = source.name,
    status = status.name,
    recurringRuleId = recurringRuleId,
    externalKey = externalKey,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

// ───────────────────────────────────────────────────────────── ricorrenti

fun RecurringRuleEntity.toDomain(): RecurringRule = RecurringRule(
    id = id,
    description = description,
    amount = Money(amountCents),
    categoryId = categoryId,
    accountId = accountId,
    every = every,
    unit = enumOrDefault(unit, RecurrenceUnit.MONTH),
    startDate = LocalDate.parse(startDate),
    endDate = endDate?.let(LocalDate::parse),
    dayOfMonth = dayOfMonth,
    active = active,
    skippedDates = skippedDates.split(',')
        .filter { it.isNotBlank() }
        .map(LocalDate::parse)
        .toSet(),
    autoInsert = autoInsert,
)

fun RecurringRule.toEntity(): RecurringRuleEntity = RecurringRuleEntity(
    id = id,
    description = description,
    amountCents = amount.cents,
    categoryId = categoryId,
    accountId = accountId,
    every = every,
    unit = unit.name,
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    dayOfMonth = dayOfMonth,
    active = active,
    skippedDates = skippedDates.sorted().joinToString(",") { it.toString() },
    autoInsert = autoInsert,
)

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback
