package com.uiery.keep.feature.routine

import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toEntity
import com.uiery.keep.model.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface RoutineRepository {
    fun fetchAll(): Flow<List<RoutineModel>>
    suspend fun fetchAllOnce(): List<RoutineModel> = unsupportedMutationBoundary()
    suspend fun fetch(id: Long): RoutineModel = unsupportedMutationBoundary()
    suspend fun insert(routine: RoutineModel): Long = unsupportedMutationBoundary()
    suspend fun update(routine: RoutineModel): Unit = unsupportedMutationBoundary()
    suspend fun deleteById(id: Long): Unit = unsupportedMutationBoundary()
    suspend fun updateIsEnabledById(id: Long, isEnabled: Boolean): Unit = unsupportedMutationBoundary()
}

private fun unsupportedMutationBoundary(): Nothing =
    error("RoutineRepository mutation/fetch boundary is not implemented by this test double")

class RoomRoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
) : RoutineRepository {
    override fun fetchAll(): Flow<List<RoutineModel>> =
        routineDao.fetchAll().map { routines -> routines.map { it.toModel() } }

    override suspend fun fetchAllOnce(): List<RoutineModel> = routineDao.fetchAllOnce().map { it.toModel() }

    override suspend fun fetch(id: Long): RoutineModel = routineDao.fetch(id).toModel()

    override suspend fun insert(routine: RoutineModel): Long = routineDao.insert(routine.toEntity())

    override suspend fun update(routine: RoutineModel) {
        routineDao.update(routine.toEntity())
    }

    override suspend fun deleteById(id: Long) {
        routineDao.deleteById(id)
    }

    override suspend fun updateIsEnabledById(id: Long, isEnabled: Boolean) {
        routineDao.updateIsEnabledById(id, isEnabled)
    }
}
