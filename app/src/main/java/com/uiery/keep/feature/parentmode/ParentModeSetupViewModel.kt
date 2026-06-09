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

    fun updateGuardianPin(pin: String) {
        _state.update { current ->
            current.copy(
                guardianPin = pin.filter(Char::isDigit).take(MAX_GUARDIAN_PIN_LENGTH),
                setupIssues = current.setupIssues - ParentModeSetupIssue.PinNotVerified,
            )
        }
    }

    fun updateGuardianPinConfirmation(pinConfirmation: String) {
        _state.update { current ->
            current.copy(
                guardianPinConfirmation = pinConfirmation.filter(Char::isDigit).take(MAX_GUARDIAN_PIN_LENGTH),
                setupIssues = current.setupIssues - ParentModeSetupIssue.PinNotVerified,
            )
        }
    }

    fun startParentModeFromSetupInput() {
        startParentMode(pinState = state.value.pinState)
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
    val guardianPin: String = "",
    val guardianPinConfirmation: String = "",
    val setupIssues: Set<ParentModeSetupIssue> = emptySet(),
    val activeSession: ParentModeSession? = null,
) {
    val pinState: ParentModePinState = if (
        guardianPin.length >= MIN_GUARDIAN_PIN_LENGTH &&
        guardianPin == guardianPinConfirmation
    ) {
        ParentModePinState.Verified
    } else if (guardianPin.isBlank() || guardianPinConfirmation.isBlank()) {
        ParentModePinState.NotConfigured
    } else {
        ParentModePinState.Failed
    }
    val canAttemptStart: Boolean = durationMinutes > 0 && allowedApps.isNotEmpty() && pinState == ParentModePinState.Verified
}

private const val MIN_GUARDIAN_PIN_LENGTH = 4
private const val MAX_GUARDIAN_PIN_LENGTH = 6

internal enum class ParentModeSetupSideEffect {
    Started,
}
