package com.uiery.keep.feature.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.model.toModel
import com.uiery.keep.util.RoutineRuntimePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val blockingStateStore: BlockingStateStore,
    private val routineDao: RoutineDao,
    private val analytics: KeepAnalytics,
) : ViewModel() {

    init {
        analytics.logScreenView(KeepAnalyticsScreen.MENU)
    }

    val preventUninstall: StateFlow<Boolean> = blockingStateStore.accessibilitySnapshot
        .map { it.preventUninstall }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isBlocking: StateFlow<Boolean> = combine(blockingStateStore.accessibilitySnapshot, routineDao.fetchAll()) { snapshot, routineEntities ->
            val isLockTime = snapshot.lockTime?.let {
                runCatching { LocalDateTime.now().isBefore(LocalDateTime.parse(it)) }
                    .getOrDefault(false)
            } ?: false
            val isRoutineActive = RoutineRuntimePolicy.isAnyRoutineActive(routineEntities.map { it.toModel() })
            snapshot.isKeep || isLockTime || isRoutineActive
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPreventUninstall(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            blockingStateStore.setPreventUninstall(enabled)
        }
    }
}
