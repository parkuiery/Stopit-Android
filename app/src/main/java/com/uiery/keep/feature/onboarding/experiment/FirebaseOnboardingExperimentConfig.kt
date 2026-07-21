package com.uiery.keep.feature.onboarding.experiment

import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import com.uiery.keep.domain.firstpromise.OnboardingVariant
import com.uiery.keep.util.AppLogger
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val ONBOARDING_VARIANT_KEY = "onboarding_variant"
private const val CONTROL_VALUE = "control"
private const val PROMISE_COACH_VALUE = "promise_coach_v1"
private const val LOG_TAG = "OnboardingRemoteConfig"
private val DIRECT_EXECUTOR = Executor { command -> command.run() }

@Singleton
class FirebaseOnboardingExperimentConfig internal constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    private val logDebug: (String, Throwable?) -> Unit,
) : OnboardingExperimentConfig {
    @Inject
    constructor(remoteConfig: FirebaseRemoteConfig) : this(
        remoteConfig = remoteConfig,
        logDebug = { message, throwable -> AppLogger.debug(LOG_TAG, message, throwable) },
    )

    override suspend fun resolve(): OnboardingExperimentResolution {
        var fetchTask: Task<Boolean>? = null
        try {
            fetchTask = remoteConfig.fetchAndActivate()
            fetchTask.await()
            logDebug("fetchAndActivate success", null)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val fetchFailure = fetchTask?.exception ?: exception
            val failureType = fetchFailure::class.java.simpleName.ifEmpty { "Unknown" }
            logDebug(
                "fetchAndActivate failed type=$failureType; using activated value",
                fetchFailure,
            )
            // Failed refreshes can still leave a previously activated remote value available below.
        }

        return readActivatedVariant().also { resolution ->
            logDebug(
                "resolved variant=${resolution.variant.name} remoteReadable=${resolution.remoteReadable}",
                null,
            )
        }
    }

    private fun readActivatedVariant(): OnboardingExperimentResolution {
        return try {
            val value = remoteConfig.getValue(ONBOARDING_VARIANT_KEY)
            logDebug("source=${value.sourceLabel()}", null)
            if (value.source != FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) {
                OnboardingExperimentResolution()
            } else {
                val rawValue = value.asString()
                logDebug("rawValue=$rawValue", null)
                OnboardingExperimentResolution(
                    variant = when (rawValue) {
                        PROMISE_COACH_VALUE -> OnboardingVariant.PromiseCoachV1
                        CONTROL_VALUE -> OnboardingVariant.Control
                        else -> OnboardingVariant.Control
                    },
                    remoteReadable = true,
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            OnboardingExperimentResolution()
        }
    }

    private fun FirebaseRemoteConfigValue.sourceLabel(): String =
        when (source) {
            FirebaseRemoteConfig.VALUE_SOURCE_REMOTE -> "remote"
            FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT -> "default"
            else -> "static"
        }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener(DIRECT_EXECUTOR) { task ->
        if (!continuation.isActive) return@addOnCompleteListener

        when {
            task.isCanceled -> continuation.cancel(CancellationException("Firebase Remote Config fetch cancelled"))
            task.exception != null -> continuation.resumeWithException(task.exception ?: return@addOnCompleteListener)
            else -> continuation.resume(task.result)
        }
    }
}
