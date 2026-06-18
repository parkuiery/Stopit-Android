package com.uiery.keep

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.MobileAds
import com.google.firebase.messaging.FirebaseMessaging
import com.uiery.keep.util.AppLogger
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.feature.routine.RoutineRoute
import com.uiery.keep.feature.splash.SplashRoute
import com.uiery.keep.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var deviceTokenManager: DeviceTokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.post {
            lifecycleScope.launch {
                delay(MobileAdsDeferredStartupDelayMillis)
                if (shouldStartMobileAdsForActivity(isFinishing, isDestroyed)) {
                    MobileAds.initialize(applicationContext)
                }
            }
            lifecycleScope.launch {
                delay(FcmTokenDeferredStartupDelayMillis)
                if (shouldFetchFcmTokenForActivity(isFinishing, isDestroyed)) {
                    fetchAndSaveFcmToken()
                }
            }
        }

        val startDestination = createMainStartDestination(intent)

        enableEdgeToEdge()
        setContent {
            KeepTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0.dp),
                ) { innerPadding ->
                    KeepApp(
                        modifier = Modifier.padding(innerPadding),
                        startDestination = startDestination,
                    )
                }
            }
        }
    }

    private fun fetchAndSaveFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                AppLogger.debug("MainActivity", task.exception.toString(), task.exception)
                return@addOnCompleteListener
            }
            lifecycleScope.launch {
                deviceTokenManager.saveDeviceToken(deviceToken = task.result)
            }
        }
    }
}

internal const val EXTRA_REPEAT_BLOCK_SURFACE = "repeat_block_surface"
internal const val EXTRA_REPEAT_BLOCK_REASON = "repeat_block_reason"
internal const val EXTRA_REPEAT_BLOCK_TIME_BUCKET = "repeat_block_time_bucket"
internal const val EXTRA_REPEAT_BLOCK_DAY_TYPE = "repeat_block_day_type"
internal const val EXTRA_REPEAT_BLOCK_CATEGORY_BUCKET = "repeat_block_category_bucket"
internal const val EXTRA_REPEAT_BLOCK_COUNT_BUCKET = "repeat_block_count_bucket"
internal const val EXTRA_REPEAT_BLOCK_COVERAGE_STATE = "repeat_block_coverage_state"
internal const val EXTRA_REPEAT_BLOCK_PREFILL_PACKAGES = "repeat_block_prefill_packages"
internal const val EXTRA_REPEAT_BLOCK_PREFILL_START_HOUR = "repeat_block_prefill_start_hour"
internal const val EXTRA_REPEAT_BLOCK_PREFILL_START_MINUTE = "repeat_block_prefill_start_minute"
internal const val EXTRA_REPEAT_BLOCK_PREFILL_END_HOUR = "repeat_block_prefill_end_hour"
internal const val EXTRA_REPEAT_BLOCK_PREFILL_END_MINUTE = "repeat_block_prefill_end_minute"

internal fun createMainStartDestination(intent: Intent): Any = createMainStartDestination(
    action = intent.action,
    routineId = intent.optionalLongExtra(NotificationHelper.EXTRA_ROUTINE_ID),
    repeatBlockSurface = intent.getStringExtra(EXTRA_REPEAT_BLOCK_SURFACE),
    repeatBlockReason = intent.getStringExtra(EXTRA_REPEAT_BLOCK_REASON),
    repeatBlockTimeBucket = intent.getStringExtra(EXTRA_REPEAT_BLOCK_TIME_BUCKET),
    repeatBlockDayType = intent.getStringExtra(EXTRA_REPEAT_BLOCK_DAY_TYPE),
    repeatBlockCategoryBucket = intent.getStringExtra(EXTRA_REPEAT_BLOCK_CATEGORY_BUCKET),
    repeatBlockCountBucket = intent.getStringExtra(EXTRA_REPEAT_BLOCK_COUNT_BUCKET),
    repeatBlockCoverageState = intent.getStringExtra(EXTRA_REPEAT_BLOCK_COVERAGE_STATE),
    prefillPackages = intent.getStringArrayListExtra(EXTRA_REPEAT_BLOCK_PREFILL_PACKAGES).orEmpty(),
    prefillStartHour = intent.optionalIntExtra(EXTRA_REPEAT_BLOCK_PREFILL_START_HOUR),
    prefillStartMinute = intent.optionalIntExtra(EXTRA_REPEAT_BLOCK_PREFILL_START_MINUTE),
    prefillEndHour = intent.optionalIntExtra(EXTRA_REPEAT_BLOCK_PREFILL_END_HOUR),
    prefillEndMinute = intent.optionalIntExtra(EXTRA_REPEAT_BLOCK_PREFILL_END_MINUTE),
)

internal fun createMainStartDestination(
    action: String?,
    routineId: Long?,
    repeatBlockSurface: String?,
    repeatBlockReason: String?,
    repeatBlockTimeBucket: String?,
    repeatBlockDayType: String?,
    repeatBlockCategoryBucket: String?,
    repeatBlockCountBucket: String?,
    repeatBlockCoverageState: String?,
    prefillPackages: List<String>,
    prefillStartHour: Int?,
    prefillStartMinute: Int?,
    prefillEndHour: Int?,
    prefillEndMinute: Int?,
): Any {
    if (action == NotificationHelper.ACTION_ROUTINE_START_NOTIFICATION_TAP) {
        return if (routineId != null) RoutineRoute() else SplashRoute
    }

    return createRepeatBlockRoutineRoute(
        repeatBlockSurface = repeatBlockSurface,
        repeatBlockReason = repeatBlockReason,
        repeatBlockTimeBucket = repeatBlockTimeBucket,
        repeatBlockDayType = repeatBlockDayType,
        repeatBlockCategoryBucket = repeatBlockCategoryBucket,
        repeatBlockCountBucket = repeatBlockCountBucket,
        repeatBlockCoverageState = repeatBlockCoverageState,
        prefillPackages = prefillPackages,
        prefillStartHour = prefillStartHour,
        prefillStartMinute = prefillStartMinute,
        prefillEndHour = prefillEndHour,
        prefillEndMinute = prefillEndMinute,
    ) ?: SplashRoute
}

internal fun createRepeatBlockRoutineRoute(intent: Intent): RoutineRoute? = createRepeatBlockRoutineRoute(
    repeatBlockSurface = intent.getStringExtra(EXTRA_REPEAT_BLOCK_SURFACE),
    repeatBlockReason = intent.getStringExtra(EXTRA_REPEAT_BLOCK_REASON),
    repeatBlockTimeBucket = intent.getStringExtra(EXTRA_REPEAT_BLOCK_TIME_BUCKET),
    repeatBlockDayType = intent.getStringExtra(EXTRA_REPEAT_BLOCK_DAY_TYPE),
    repeatBlockCategoryBucket = intent.getStringExtra(EXTRA_REPEAT_BLOCK_CATEGORY_BUCKET),
    repeatBlockCountBucket = intent.getStringExtra(EXTRA_REPEAT_BLOCK_COUNT_BUCKET),
    repeatBlockCoverageState = intent.getStringExtra(EXTRA_REPEAT_BLOCK_COVERAGE_STATE),
    prefillPackages = intent.getStringArrayListExtra(EXTRA_REPEAT_BLOCK_PREFILL_PACKAGES).orEmpty(),
    prefillStartHour = intent.optionalIntExtra(EXTRA_REPEAT_BLOCK_PREFILL_START_HOUR),
    prefillStartMinute = intent.optionalIntExtra(EXTRA_REPEAT_BLOCK_PREFILL_START_MINUTE),
    prefillEndHour = intent.optionalIntExtra(EXTRA_REPEAT_BLOCK_PREFILL_END_HOUR),
    prefillEndMinute = intent.optionalIntExtra(EXTRA_REPEAT_BLOCK_PREFILL_END_MINUTE),
)

internal fun createRepeatBlockRoutineRoute(
    repeatBlockSurface: String?,
    repeatBlockReason: String?,
    repeatBlockTimeBucket: String?,
    repeatBlockDayType: String?,
    repeatBlockCategoryBucket: String?,
    repeatBlockCountBucket: String?,
    repeatBlockCoverageState: String?,
    prefillPackages: List<String>,
    prefillStartHour: Int?,
    prefillStartMinute: Int?,
    prefillEndHour: Int?,
    prefillEndMinute: Int?,
): RoutineRoute? {
    if (
        repeatBlockSurface.isNullOrBlank() ||
        repeatBlockReason.isNullOrBlank() ||
        repeatBlockTimeBucket.isNullOrBlank() ||
        repeatBlockDayType.isNullOrBlank() ||
        repeatBlockCategoryBucket.isNullOrBlank() ||
        repeatBlockCountBucket.isNullOrBlank() ||
        repeatBlockCoverageState.isNullOrBlank() ||
        prefillPackages.isEmpty() ||
        prefillStartHour == null ||
        prefillStartMinute == null ||
        prefillEndHour == null ||
        prefillEndMinute == null
    ) {
        return null
    }
    return RoutineRoute(
        repeatBlockSurface = repeatBlockSurface,
        repeatBlockReason = repeatBlockReason,
        repeatBlockTimeBucket = repeatBlockTimeBucket,
        repeatBlockDayType = repeatBlockDayType,
        repeatBlockCategoryBucket = repeatBlockCategoryBucket,
        repeatBlockCountBucket = repeatBlockCountBucket,
        repeatBlockCoverageState = repeatBlockCoverageState,
        prefillPackages = prefillPackages,
        prefillStartHour = prefillStartHour,
        prefillStartMinute = prefillStartMinute,
        prefillEndHour = prefillEndHour,
        prefillEndMinute = prefillEndMinute,
    )
}

private fun Intent.optionalIntExtra(name: String): Int? =
    if (hasExtra(name)) getIntExtra(name, 0) else null

private fun Intent.optionalLongExtra(name: String): Long? =
    if (hasExtra(name)) getLongExtra(name, 0L) else null

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    KeepTheme {
        KeepApp()
    }
}