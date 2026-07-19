package com.uiery.keep.feature.onboarding.experiment

import com.google.android.gms.tasks.Task
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.uiery.keep.domain.firstpromise.OnboardingVariant
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
private val DIRECT_EXECUTOR = Executor { command -> command.run() }

@Singleton
class FirebaseOnboardingExperimentConfig @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) : OnboardingExperimentConfig {
    override suspend fun resolve(): OnboardingExperimentResolution {
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Failed refreshes can still leave a previously activated remote value available below.
        }

        return readActivatedVariant()
    }

    private fun readActivatedVariant(): OnboardingExperimentResolution {
        return try {
            val value = remoteConfig.getValue(ONBOARDING_VARIANT_KEY)
            if (value.source != FirebaseRemoteConfig.VALUE_SOURCE_REMOTE) {
                OnboardingExperimentResolution()
            } else {
                OnboardingExperimentResolution(
                    variant = when (value.asString()) {
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
