package com.uiery.keep.feature.parentmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.domain.parentmode.ParentModeSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Parent mode's list is an allowlist: the apps named here are the only ones that stay open, and
 * every other app on the device is locked for the duration. That is the opposite of the blocking
 * selection the rest of the app keeps, so this screen deliberately does not read it — seeding one
 * from the other handed parents an allowlist made of the apps they had already chosen to block,
 * which locked everything else and reads as "parent mode locks every app".
 */
@HiltViewModel
internal class ParentModeSetupViewModel @Inject constructor(
    private val sessionController: ParentModeSessionController,
    private val clock: ParentModeClock,
    private val analytics: KeepAnalytics,
) : ViewModel() {
    private val _state = MutableStateFlow(ParentModeSetupUiState())
    val state: StateFlow<ParentModeSetupUiState> = _state.asStateFlow()

    private val _sideEffect = MutableStateFlow<ParentModeSetupSideEffect?>(null)
    val sideEffect: StateFlow<ParentModeSetupSideEffect?> = _sideEffect.asStateFlow()

    init {
        analytics.logScreenView(KeepAnalyticsScreen.PARENT_MODE_SETUP)
    }

    /** Preset shortcut. It moves the same wheel the parent would otherwise dial by hand. */
    fun setDurationMinutes(durationMinutes: Int) {
        _state.update { current ->
            current.copy(
                durationMinutes = durationMinutes,
                setupIssues = current.setupIssues - ParentModeSetupIssue.InvalidDuration,
            )
        }
    }

    fun setDurationParts(hours: Int, minutes: Int) {
        setDurationMinutes(hours.coerceAtLeast(0) * MINUTES_PER_HOUR + minutes.coerceAtLeast(0))
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

    /**
     * The guardian PIN is the gate that hands the phone over, not another field on the form, so it
     * is asked for once — in a sheet — at the moment the parent commits to an action.
     */
    fun requestGuardianAction(action: ParentModeGuardianAction) {
        _state.update { current ->
            current.copy(
                pendingGuardianAction = action,
                guardianPin = "",
                guardianPinConfirmation = "",
                setupIssues = current.setupIssues - ParentModeSetupIssue.PinNotVerified,
            )
        }
    }

    fun dismissGuardianAction() {
        _state.update { current ->
            current.copy(
                pendingGuardianAction = null,
                guardianPin = "",
                guardianPinConfirmation = "",
                setupIssues = current.setupIssues - ParentModeSetupIssue.PinNotVerified,
            )
        }
    }

    fun confirmPendingGuardianAction() {
        when (state.value.pendingGuardianAction) {
            ParentModeGuardianAction.Start -> startParentModeFromSetupInput()
            ParentModeGuardianAction.Extend -> extendActiveSessionByTenMinutes()
            ParentModeGuardianAction.End -> endActiveSessionFromSetupInput()
            null -> Unit
        }
    }

    fun startParentModeFromSetupInput() {
        startParentMode(pinState = state.value.pinState)
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
                ParentModeSessionControllerResult.Cleared,
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
                is ParentModeSessionControllerResult.Expired -> {
                    updateActiveSession(result.session, ParentModeSetupSideEffect.Expired)
                }
                ParentModeSessionControllerResult.PinRequired -> {
                    _state.update { current ->
                        current.copy(setupIssues = setOf(ParentModeSetupIssue.PinNotVerified))
                    }
                }
                ParentModeSessionControllerResult.InvalidExtension,
                ParentModeSessionControllerResult.Cleared,
                ParentModeSessionControllerResult.NoActiveSession,
                is ParentModeSessionControllerResult.Ended,
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
                is ParentModeSessionControllerResult.Expired -> {
                    updateActiveSession(result.session, ParentModeSetupSideEffect.Expired)
                }
                ParentModeSessionControllerResult.PinRequired -> {
                    _state.update { current ->
                        current.copy(setupIssues = setOf(ParentModeSetupIssue.PinNotVerified))
                    }
                }
                ParentModeSessionControllerResult.InvalidExtension,
                ParentModeSessionControllerResult.Cleared,
                ParentModeSessionControllerResult.NoActiveSession,
                is ParentModeSessionControllerResult.Extended,
                is ParentModeSessionControllerResult.NoStateChange,
                is ParentModeSessionControllerResult.SetupBlocked,
                is ParentModeSessionControllerResult.Started,
                -> Unit
            }
        }
    }

    fun refreshActiveSessionStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            applyActiveSessionStatus(sessionController.markExpiredIfNeeded(clock.nowMillis()))
        }
    }

    fun prepareAnotherParentModeSession() {
        viewModelScope.launch(Dispatchers.IO) {
            when (sessionController.clearFinishedSession()) {
                ParentModeSessionControllerResult.Cleared,
                ParentModeSessionControllerResult.NoActiveSession,
                -> {
                    _state.update { current ->
                        current.copy(
                            setupIssues = emptySet(),
                            activeSession = null,
                            guardianPin = "",
                            guardianPinConfirmation = "",
                            pendingGuardianAction = null,
                        )
                    }
                }
                is ParentModeSessionControllerResult.NoStateChange,
                ParentModeSessionControllerResult.InvalidExtension,
                ParentModeSessionControllerResult.PinRequired,
                is ParentModeSessionControllerResult.Ended,
                is ParentModeSessionControllerResult.Expired,
                is ParentModeSessionControllerResult.Extended,
                is ParentModeSessionControllerResult.SetupBlocked,
                is ParentModeSessionControllerResult.Started,
                -> Unit
            }
        }
    }

    private fun applyActiveSessionStatus(result: ParentModeSessionControllerResult) {
        when (result) {
            is ParentModeSessionControllerResult.Expired -> {
                updateActiveSession(result.session, ParentModeSetupSideEffect.Expired)
            }
            is ParentModeSessionControllerResult.NoStateChange -> {
                _state.update { current -> current.copy(activeSession = result.session) }
            }
            ParentModeSessionControllerResult.InvalidExtension,
            ParentModeSessionControllerResult.Cleared,
            ParentModeSessionControllerResult.NoActiveSession,
            ParentModeSessionControllerResult.PinRequired,
            is ParentModeSessionControllerResult.Ended,
            is ParentModeSessionControllerResult.Extended,
            is ParentModeSessionControllerResult.SetupBlocked,
            is ParentModeSessionControllerResult.Started,
            -> Unit
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
                guardianPin = "",
                guardianPinConfirmation = "",
                pendingGuardianAction = null,
            )
        }
        _sideEffect.value = sideEffect
    }
}

private const val DEFAULT_EXTENSION_MINUTES = 10

/** What the guardian PIN sheet is currently standing in front of. */
internal enum class ParentModeGuardianAction {
    Start,
    Extend,
    End,
}

internal data class ParentModeSetupUiState(
    val durationMinutes: Int = 10,
    val allowedApps: Set<String> = emptySet(),
    val guardianPin: String = "",
    val guardianPinConfirmation: String = "",
    val setupIssues: Set<ParentModeSetupIssue> = emptySet(),
    val activeSession: ParentModeSession? = null,
    val pendingGuardianAction: ParentModeGuardianAction? = null,
) {
    val durationHoursPart: Int = durationMinutes / MINUTES_PER_HOUR
    val durationMinutesPart: Int = durationMinutes % MINUTES_PER_HOUR
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

    /** The promise itself is complete without a PIN; the PIN is asked for in the sheet after this. */
    val canRequestStart: Boolean = durationMinutes > 0 && allowedApps.isNotEmpty()
    val canAttemptStart: Boolean = canRequestStart && pinState == ParentModePinState.Verified
}

internal const val MINUTES_PER_HOUR = 60
private const val MIN_GUARDIAN_PIN_LENGTH = 4
private const val MAX_GUARDIAN_PIN_LENGTH = 6

internal enum class ParentModeSetupSideEffect {
    Started,
    Extended,
    Ended,
    Expired,
}
