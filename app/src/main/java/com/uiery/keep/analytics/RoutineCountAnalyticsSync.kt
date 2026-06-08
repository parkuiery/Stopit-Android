package com.uiery.keep.analytics

import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.database.entity.RoutineEntity
import javax.inject.Inject

object KeepAnalyticsUserProperty {
    const val ROUTINES_COUNT = "routines_count"
}

class RoutineCountAnalyticsSync
    @Inject
    constructor(
        private val routineDao: RoutineDao,
        private val analytics: KeepAnalytics,
    ) {
        fun syncFromRoom() {
            syncFromRoutines(routineDao.fetchAllOnce())
        }

        fun syncFromRoutines(routines: List<RoutineEntity>) {
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
