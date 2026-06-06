package com.uiery.keep.feature.routine

import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.model.RoutineModel
import com.uiery.keep.model.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface RoutineRepository {
    fun fetchAll(): Flow<List<RoutineModel>>
}

class RoomRoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
) : RoutineRepository {
    override fun fetchAll(): Flow<List<RoutineModel>> =
        routineDao.fetchAll().map { routines -> routines.map { it.toModel() } }
}
