package com.uiery.keep.analytics

import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.model.RoutineModel
import javax.inject.Inject

object KeepAnalyticsUserProperty {
    const val ROUTINES_COUNT = "routines_count"
}

class RoutineCountAnalyticsSync
    @Inject
    constructor(
        private val routineRepository: RoutineRepository,
        private val analytics: KeepAnalytics,
    ) {
        suspend fun syncFromRepository() {
            syncFromRoutines(routineRepository.fetchAllOnce())
        }

        fun syncFromRoutines(routines: List<RoutineModel>) {
            syncCount(routines.size)
        }

        fun syncCount(count: Int) {
            analytics.setRoutinesCount(count)
        }
    }

fun KeepAnalytics.setRoutinesCount(count: Int) {
    setUserProperty(
        name = KeepAnalyticsUserProperty.ROUTINES_COUNT,
        value = count.coerceAtLeast(0).toString(),
    )
}
