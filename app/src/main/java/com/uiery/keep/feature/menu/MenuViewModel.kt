package com.uiery.keep.feature.menu

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.KeepDataSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.model.toModel
import com.uiery.keep.util.RoutineRuntimePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
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
    @KeepDataSource private val dataStore: DataStore<Preferences>,
    private val routineDao: RoutineDao,
    private val analytics: KeepAnalytics,
) : ViewModel() {

    init {
        analytics.logScreenView(KeepAnalyticsScreen.MENU)
    }

    val preventUninstall: StateFlow<Boolean> = dataStore.data
        .map { it[PreferencesKey.PREVENT_UNINSTALL] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isBlocking: StateFlow<Boolean> = combine(dataStore.data, routineDao.fetchAll()) { prefs, routineEntities ->
            val isKeep = prefs[PreferencesKey.IS_KEEP] ?: false
            val isLockTime = prefs[PreferencesKey.LOCK_TIME]?.let {
                runCatching { LocalDateTime.now().isBefore(LocalDateTime.parse(it)) }
                    .getOrDefault(false)
            } ?: false
            val isRoutineActive = RoutineRuntimePolicy.isAnyRoutineActive(routineEntities.map { it.toModel() })
            isKeep || isLockTime || isRoutineActive
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPreventUninstall(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKey.PREVENT_UNINSTALL] = enabled
            }
        }
    }
}
