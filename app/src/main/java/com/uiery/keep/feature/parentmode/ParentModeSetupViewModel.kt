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
                guardianPinRejected = false,
                setupIssues = current.setupIssues - ParentModeSetupIssue.PinNotVerified,
            )
        }
    }

    fun updateGuardianPinConfirmation(pinConfirmation: String) {
        _state.update { current ->
            current.copy(
                guardianPinConfirmation = pinConfirmation.filter(Char::isDigit).take(MAX_GUARDIAN_PIN_LENGTH),
                guardianPinRejected = false,
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
                guardianPinRejected = false,
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
                guardianPinRejected = false,
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
        val snapshot = state.value
        startParentMode(
            guardianPin = snapshot.guardianPin,
            guardianPinConfirmation = snapshot.guardianPinConfirmation,
        )
    }

    fun startParentMode(
        guardianPin: String,
        guardianPinConfirmation: String,
    ) {
        val snapshot = state.value
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = sessionController.start(
                durationMinutes = snapshot.durationMinutes,
                allowedApps = snapshot.allowedApps,
                guardianPin = guardianPin,
                guardianPinConfirmation = guardianPinConfirmation,
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
                pinAttempt = state.value.guardianPin,
                nowMillis = clock.nowMillis(),
            )) {
                is ParentModeSessionControllerResult.Extended -> {
                    updateActiveSession(result.session, ParentModeSetupSideEffect.Extended)
                }
                is ParentModeSessionControllerResult.Expired -> {
                    updateActiveSession(result.session, ParentModeSetupSideEffect.Expired)
                }
                ParentModeSessionControllerResult.PinRequired -> rejectGuardianPin()
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
                pinAttempt = state.value.guardianPin,
                nowMillis = clock.nowMillis(),
            )) {
                is ParentModeSessionControllerResult.Ended -> {
                    updateActiveSession(result.session, ParentModeSetupSideEffect.Ended)
                }
                is ParentModeSessionControllerResult.Expired -> {
                    updateActiveSession(result.session, ParentModeSetupSideEffect.Expired)
                }
                ParentModeSessionControllerResult.PinRequired -> rejectGuardianPin()
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
                            guardianPinRejected = false,
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

    /**
     * The sheet stays open and the box empties. A wrong PIN is the case this gate exists for, so it
     * has to be retryable in place — and the digits that failed are worth nothing to the next try.
     */
    private fun rejectGuardianPin() {
        _state.update { current ->
            current.copy(
                guardianPin = "",
                guardianPinConfirmation = "",
                guardianPinRejected = true,
                setupIssues = setOf(ParentModeSetupIssue.PinNotVerified),
            )
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
                guardianPinRejected = false,
                pendingGuardianAction = null,
            )
        }
        _sideEffect.value = sideEffect
    }
}

private const val DEFAULT_EXTENSION_MINUTES = 10

/** What the guardian PIN sheet is currently standing in front of. */
internal enum class ParentModeGuardianAction(val confirmsNewPin: Boolean) {
    /** Sets the PIN. It is typed twice because there is nothing stored yet to check it against. */
    Start(confirmsNewPin = true),

    /** Checked against what [Start] stored, so one box is the entire question. Asking for a second
     *  box here is what made the gate meaningless: two boxes can only agree with each other. */
    Extend(confirmsNewPin = false),
    End(confirmsNewPin = false),
}

internal data class ParentModeSetupUiState(
    val durationMinutes: Int = 10,
    val allowedApps: Set<String> = emptySet(),
    val guardianPin: String = "",
    val guardianPinConfirmation: String = "",
    /** The last attempt was held against the stored PIN and did not match. */
    val guardianPinRejected: Boolean = false,
    val setupIssues: Set<ParentModeSetupIssue> = emptySet(),
    val activeSession: ParentModeSession? = null,
    val pendingGuardianAction: ParentModeGuardianAction? = null,
) {
    val durationHoursPart: Int = durationMinutes / MINUTES_PER_HOUR
    val durationMinutesPart: Int = durationMinutes % MINUTES_PER_HOUR

    /**
     * Only ever describes the *start* of a session — two boxes agreeing with each other.
     *
     * Extending or ending is not a question this screen can answer: it is settled against the hash
     * the session stored, which lives behind [ParentModeSessionController].
     */
    val pinState: ParentModePinState = ParentModePolicy.setupPinState(
        pin = guardianPin,
        confirmation = guardianPinConfirmation,
    )

    /** The promise itself is complete without a PIN; the PIN is asked for in the sheet after this. */
    val canRequestStart: Boolean = durationMinutes > 0 && allowedApps.isNotEmpty()
    val canAttemptStart: Boolean = canRequestStart && pinState == ParentModePinState.Verified

    /**
     * Whether the sheet's button can fire — not whether the PIN is right.
     *
     * For [ParentModeGuardianAction.Start] that is the same thing. For the others it deliberately
     * is not: only the stored hash can settle those, so the button unlocks on a plausible entry and
     * the verdict comes back from the controller.
     */
    fun canConfirmGuardianAction(action: ParentModeGuardianAction): Boolean =
        if (action.confirmsNewPin) {
            pinState == ParentModePinState.Verified
        } else {
            guardianPin.length >= MIN_GUARDIAN_PIN_LENGTH
        }
}

internal const val MINUTES_PER_HOUR = 60
private const val MAX_GUARDIAN_PIN_LENGTH = 6

internal enum class ParentModeSetupSideEffect {
    Started,
    Extended,
    Ended,
    Expired,
}
