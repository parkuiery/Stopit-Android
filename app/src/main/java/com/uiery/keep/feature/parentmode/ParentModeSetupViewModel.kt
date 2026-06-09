package com.uiery.keep.feature.parentmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.datastore.BlockingStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ParentModeSetupViewModel @Inject constructor(
    private val blockingStateStore: BlockingStateStore,
    private val sessionController: ParentModeSessionController,
    private val clock: ParentModeClock,
) : ViewModel() {
    private val _state = MutableStateFlow(ParentModeSetupUiState())
    val state: StateFlow<ParentModeSetupUiState> = _state.asStateFlow()

    private val _sideEffect = MutableStateFlow<ParentModeSetupSideEffect?>(null)
    val sideEffect: StateFlow<ParentModeSetupSideEffect?> = _sideEffect.asStateFlow()

    fun setDurationMinutes(durationMinutes: Int) {
        _state.update { current ->
            current.copy(
                durationMinutes = durationMinutes,
                setupIssues = current.setupIssues - ParentModeSetupIssue.InvalidDuration,
            )
        }
    }

    fun setAllowedApps(allowedApps: Set<String>) {
        _state.update { current ->
            current.copy(
                allowedApps = allowedApps,
                setupIssues = current.setupIssues - ParentModeSetupIssue.NoAllowedApps,
            )
        }
    }

    fun loadAllowedAppsFromCurrentSelection() {
        viewModelScope.launch(Dispatchers.IO) {
            setAllowedApps(blockingStateStore.readSelectedAppPackages())
        }
    }

    fun startParentMode(pinState: ParentModePinState) {
        val snapshot = state.value
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = sessionController.start(
                durationMinutes = snapshot.durationMinutes,
                allowedApps = snapshot.allowedApps,
                pinState = pinState,
                nowMillis = clock.nowMillis(),
            )) {
                is ParentModeSessionControllerResult.SetupBlocked -> {
                    _state.update { current -> current.copy(setupIssues = result.issues) }
                }
                is ParentModeSessionControllerResult.Started -> {
                    _state.update { current ->
                        current.copy(
                            setupIssues = emptySet(),
                            activeSession = result.session,
                        )
                    }
                    _sideEffect.value = ParentModeSetupSideEffect.Started
                }
                is ParentModeSessionControllerResult.Extended,
                is ParentModeSessionControllerResult.Ended,
                ParentModeSessionControllerResult.InvalidExtension,
                ParentModeSessionControllerResult.PinRequired,
                ParentModeSessionControllerResult.NoActiveSession,
                -> Unit
            }
        }
    }
}

internal data class ParentModeSetupUiState(
    val durationMinutes: Int = 10,
    val allowedApps: Set<String> = emptySet(),
    val setupIssues: Set<ParentModeSetupIssue> = emptySet(),
    val activeSession: ParentModeSession? = null,
) {
    val canAttemptStart: Boolean = durationMinutes > 0 && allowedApps.isNotEmpty()
}

internal enum class ParentModeSetupSideEffect {
    Started,
}
