package com.uiery.keep.feature.splash

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.KeepDataSource
import com.uiery.keep.database.dao.RoutineDao
import com.uiery.keep.datastore.PreferencesKey
import com.uiery.keep.model.toModel
import com.uiery.keep.network.Retrofit
import com.uiery.keep.network.device.RegisterDeviceRequest
import com.uiery.keep.util.deviceId
import com.uiery.keep.util.toDayOfWeekList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.toKotlinLocalDateTime
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.LocalDateTime
import java.util.TimeZone
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    @KeepDataSource private val dataStore: DataStore<Preferences>,
) : ContainerHost<SplashUiState, SplashSideEffect>, ViewModel() {

    override val container: Container<SplashUiState, SplashSideEffect> = container(SplashUiState())

    init {
        //getIsNew()
        //getLockTime()
        navigateScreen()
        //registerDevice()
    }

    private fun navigateScreen() = intent {
        coroutineScope {
            val delayDeferred = async { delay(0.7.seconds) }
            val handleDeferred = async { handleNavigate() }

            awaitAll(delayDeferred, handleDeferred)
            postSideEffect(handleDeferred.await())
        }
//        delay(700)
//        if (state.isNew) {
//            postSideEffect(SplashSideEffect.MoveToOnboarding)
//        } else if (state.isLock) {
//            val lockTime = state.lockTime
//            postSideEffect(SplashSideEffect.MoveToLock(lockTime = lockTime))
//        } else {
//            postSideEffect(SplashSideEffect.MoveToHome)
//        }
    }

    private suspend fun handleNavigate(): SplashSideEffect {
        if (getIsNew()) {
            return SplashSideEffect.MoveToOnboarding
        } else {
            val lockTime = getLockTime()
            val isLock = runCatching { LocalDateTime.now() < LocalDateTime.parse(lockTime) }.getOrNull() ?: false
            val routines = checkRoutines()
            return when {
                isLock && lockTime != null -> SplashSideEffect.MoveToLock(lockTime = lockTime, false)
                routines -> SplashSideEffect.MoveToLock(null,true)
                else -> SplashSideEffect.MoveToHome
            }
        }
    }

    private suspend fun getIsNew(): Boolean {
        val isNew = dataStore.data.map { preferences ->
            preferences[PreferencesKey.IS_NEW]
        }.firstOrNull()

        return isNew ?: true
        //reduce { state.copy(isNew = isNew ?: true) }
    }

    private suspend fun getLockTime(): String? {
        val lockTime = dataStore.data.map { preferences ->
            preferences[PreferencesKey.LOCK_TIME]
        }.firstOrNull()

        return lockTime
//        lockTime?.let {
//            val isLock = LocalDateTime.now() < LocalDateTime.parse(it)
//            reduce { state.copy(isLock = isLock, lockTime = it) }
//        }
    }

    private suspend fun checkRoutines(): Boolean {
        val routines = routineDao.fetchAll().firstOrNull()?.map { it.toModel() }
        return routines?.filter { it.isEnabled }
            ?.filter { it.repeatDays.toDayOfWeekList().contains(LocalDateTime.now().dayOfWeek) }
            ?.filter { it.startTime.rangeUntil(it.endTime).contains(LocalDateTime.now().toKotlinLocalDateTime().time) }?.isNotEmpty() ?: false
    }

    private fun registerDevice() = intent {
        runCatching {
            val deviceService = Retrofit.deviceService
            val fcmToken = dataStore.data.map { preferences ->
                preferences[PreferencesKey.FCM_TOKEN]
            }.firstOrNull()

            deviceService.registerDevice(
                registerDeviceRequest = RegisterDeviceRequest(
                    deviceId = deviceId(),
                    fcmToken = fcmToken ?: "",
                    timeZone = TimeZone.getDefault().id,
                    platform = "ANDROID",
                )
            )
        }
    }
}

data class SplashUiState(
    val isNew: Boolean = false,
    val isLock: Boolean = false,
    val lockTime: String = LocalDateTime.now().toString(),
)

sealed class SplashSideEffect {
    data object MoveToHome : SplashSideEffect()
    data object MoveToOnboarding : SplashSideEffect()
    data class MoveToLock(val lockTime: String?, val isRoutine: Boolean) : SplashSideEffect()
}