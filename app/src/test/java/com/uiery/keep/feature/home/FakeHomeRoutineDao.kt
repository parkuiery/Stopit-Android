package com.uiery.keep.feature.home

import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeHomeRoutineDao(
    routines: List<RoutineEntity> = emptyList(),
) : RoutineDao {
    private val state = MutableStateFlow(routines)

    override fun fetchAll(): Flow<List<RoutineEntity>> = state
    override fun fetchAllOnce(): List<RoutineEntity> = state.value
    override fun fetch(id: Long): RoutineEntity = state.value.first { it.id == id }
    override fun insert(routineEntity: RoutineEntity): Long {
        state.value = state.value + routineEntity
        return routineEntity.id
    }
    override fun deleteById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
    override fun update(routineEntity: RoutineEntity) {
        state.value = state.value.map { if (it.id == routineEntity.id) routineEntity else it }
    }
    override fun updateIsEnabledById(id: Long, isEnabled: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(isEnabled = isEnabled) else it }
    }
}
