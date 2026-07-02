package com.uiery.keep.feature.home

import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class EmptyHomeRoutineDao : RoutineDao {
    override fun fetchAll(): Flow<List<RoutineEntity>> = flowOf(emptyList())

    override fun fetchAllOnce(): List<RoutineEntity> = emptyList()

    override fun fetch(id: Long): RoutineEntity = error("No routine for id=$id")

    override fun insert(routineEntity: RoutineEntity): Long = routineEntity.id

    override fun deleteById(id: Long) = Unit

    override fun update(routineEntity: RoutineEntity) = Unit

    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) = Unit
}
