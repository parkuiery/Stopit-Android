package com.uiery.keep.feature.parentmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.datastore.BlockingStateStore
import com.uiery.keep.domain.parentmode.ParentModeSession
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
                    updateActiveSession(result.session, ParentModeSetupSideEffect.Started)
                }
                is ParentModeSessionControllerResult.Extended,
                is ParentModeSessionControllerResult.Ended,
                is ParentModeSessionControllerResult.Expired,
                is ParentModeSessionControllerResult.NoStateChange,
                ParentModeSessionControllerResult.InvalidExtension,
                ParentModeSessionControllerResult.PinRequired,
                ParentModeSessionControllerResult.NoActiveSession,
                -> Unit
            }
        }
    }

    fun extendActiveSessionByTenMinutes() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = sessionController.extend(
                extensionMinutes = DEFAULT_EXTENSION_MINUTES,
                pinState = state.value.pinState,
                nowMillis = clock.nowMillis(),
            )) {
                is ParentModeSessionControllerResult.Extended -> {
                    updateActiveSession(result.session, ParentModeSetupSideEffect.Extended)
                }
                ParentModeSessionControllerResult.PinRequired -> {
                    _state.update { current ->
                        current.copy(setupIssues = setOf(ParentModeSetupIssue.PinNotVerified))
                    }
                }
                ParentModeSessionControllerResult.InvalidExtension,
                ParentModeSessionControllerResult.NoActiveSession,
                is ParentModeSessionControllerResult.Ended,
                is ParentModeSessionControllerResult.Expired,
                is ParentModeSessionControllerResult.NoStateChange,
                is ParentModeSessionControllerResult.SetupBlocked,
                is ParentModeSessionControllerResult.Started,
                -> Unit
            }
        }
    }

    fun endActiveSessionFromSetupInput() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = sessionController.endNow(
                pinState = state.value.pinState,
                nowMillis = clock.nowMillis(),
            )) {
                is ParentModeSessionControllerResult.Ended -> {
                    updateActiveSession(result.session, ParentModeSetupSideEffect.Ended)
                }
                ParentModeSessionControllerResult.PinRequired -> {
                    _state.update { current ->
                        current.copy(setupIssues = setOf(ParentModeSetupIssue.PinNotVerified))
                    }
                }
                ParentModeSessionControllerResult.InvalidExtension,
                ParentModeSessionControllerResult.NoActiveSession,
                is ParentModeSessionControllerResult.Extended,
                is ParentModeSessionControllerResult.Expired,
                is ParentModeSessionControllerResult.NoStateChange,
                is ParentModeSessionControllerResult.SetupBlocked,
                is ParentModeSessionControllerResult.Started,
                -> Unit
            }
        }
    }

    fun refreshActiveSessionStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = sessionController.markExpiredIfNeeded(clock.nowMillis())) {
                is ParentModeSessionControllerResult.Expired -> {
                    updateActiveSession(result.session, ParentModeSetupSideEffect.Expired)
                }
                is ParentModeSessionControllerResult.NoStateChange -> {
                    _state.update { current -> current.copy(activeSession = result.session) }
                }
                ParentModeSessionControllerResult.InvalidExtension,
                ParentModeSessionControllerResult.NoActiveSession,
                ParentModeSessionControllerResult.PinRequired,
                is ParentModeSessionControllerResult.Ended,
                is ParentModeSessionControllerResult.Extended,
                is ParentModeSessionControllerResult.SetupBlocked,
                is ParentModeSessionControllerResult.Started,
                -> Unit
            }
        }
    }

    private fun updateActiveSession(
        session: ParentModeSession,
        sideEffect: ParentModeSetupSideEffect,
    ) {
        _state.update { current ->
            current.copy(
                setupIssues = emptySet(),
                activeSession = session,
            )
        }
        _sideEffect.value = sideEffect
    }
}

private const val DEFAULT_EXTENSION_MINUTES = 10

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
    Extended,
    Ended,
    Expired,
}
