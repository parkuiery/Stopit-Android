package com.uiery.keep.feature.splash

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import com.uiery.keep.KeepDataSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.PreferencesKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class SplashViewModel
    @Inject
    constructor(
        @KeepDataSource private val dataStore: DataStore<Preferences>,
        private val analytics: KeepAnalytics,
    ) : ViewModel(),
        ContainerHost<SplashUiState, SplashSideEffect> {
        override val container: Container<SplashUiState, SplashSideEffect> = container(SplashUiState())

        init {
            navigateScreen()
        }

        private fun navigateScreen() =
            intent {
                delay(0.7.seconds)
                postSideEffect(handleNavigate())
            }

        private suspend fun handleNavigate(): SplashSideEffect {
            if (getIsNew()) {
                trackFirstOpenIfNeeded()
                return SplashSideEffect.MoveToOnboarding
            }

            val lockTime = getLockTime()
            val isLock = runCatching { LocalDateTime.now() < LocalDateTime.parse(lockTime) }.getOrNull() ?: false
            return when {
                isLock && lockTime != null -> SplashSideEffect.MoveToLock(lockTime = lockTime, false)
                else -> SplashSideEffect.MoveToHome
            }
        }

        private suspend fun trackFirstOpenIfNeeded() {
            val hasTracked =
                dataStore.data
                    .map { preferences ->
                        preferences[PreferencesKey.HAS_TRACKED_FIRST_OPEN] == true
                    }.firstOrNull() == true

            if (hasTracked) return

            analytics.trackFirstOpen()
            dataStore.edit { preferences ->
                preferences[PreferencesKey.HAS_TRACKED_FIRST_OPEN] = true
                preferences[PreferencesKey.FIRST_OPEN_TIMESTAMP] = System.currentTimeMillis()
            }
        }

        private suspend fun getIsNew(): Boolean {
            val isNew =
                dataStore.data
                    .map { preferences ->
                        preferences[PreferencesKey.IS_NEW]
                    }.firstOrNull()

            return isNew ?: true
        }

        private suspend fun getLockTime(): String? {
            val lockTime =
                dataStore.data
                    .map { preferences ->
                        preferences[PreferencesKey.LOCK_TIME]
                    }.firstOrNull()

            return lockTime
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

    data class MoveToLock(
        val lockTime: String?,
        val isRoutine: Boolean,
    ) : SplashSideEffect()
}
