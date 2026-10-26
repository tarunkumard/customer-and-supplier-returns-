package com.tarun.managemyshop.data.repository

import com.tarun.managemyshop.data.local.dao.ExpenseDao
import com.tarun.managemyshop.data.local.entity.ExpenseEntity
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.repository.ExpenseRepository
import com.tarun.managemyshop.domain.validation.ExpenseValidator
import kotlinx.coroutines.flow.Flow

class ExpenseRepositoryImpl(private val dao: ExpenseDao) : ExpenseRepository {

    override fun observeFiltered(query: String, start: Long, end: Long): Flow<List<ExpenseEntity>> =
        dao.observeFiltered(query.trim(), start, end)

    override fun observeTotalBetween(start: Long, end: Long): Flow<Long> =
        dao.observeTotalBetween(start, end)

    override suspend fun getById(id: Long): ExpenseEntity? = dao.getById(id)

    override suspend fun save(expense: ExpenseEntity): SaveResult<Long> {
        val cleaned = expense.copy(title = expense.title.trim(), category = expense.category.trim())
        val errors = ExpenseValidator.validate(cleaned)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)

        return if (cleaned.id == 0L) {
            SaveResult.Success(dao.insert(cleaned))
        } else {
            dao.update(cleaned)
            SaveResult.Success(cleaned.id)
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}
