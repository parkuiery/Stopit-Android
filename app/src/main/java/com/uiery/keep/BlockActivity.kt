package com.uiery.keep

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.analytics.routine.RepeatBlockRoutineSuggestionSurface
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestion
import com.uiery.keep.lockscreen.LockScreenEntry
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BlockActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deferMobileAdsInitialization()
        val args = createBlockActivityArgs(
            packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME),
            blockSource = intent.getStringExtra(EXTRA_BLOCK_SOURCE),
            rawRoutineId = intent.extras?.getCompat(EXTRA_ROUTINE_ID),
            rawGoalLockId = intent.extras?.getCompat(EXTRA_GOAL_LOCK_ID),
        )
        enableEdgeToEdge()
        setContent {
            KeepTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = KeepTheme.colors.background,
                ) { innerPadding ->
                    BlockScreen(
                        modifier = Modifier.padding(innerPadding),
                        packageName = args.lockScreenEntry.blockedPackageName,
                        blockSource = args.lockScreenEntry.blockSource,
                        routineId = args.lockScreenEntry.routineId,
                        goalLockId = args.lockScreenEntry.goalLockId,
                        onClose = {
                            val homeIntent = Intent(Intent.ACTION_MAIN)
                            homeIntent.addCategory(Intent.CATEGORY_HOME)
                            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(homeIntent)
                            finishAffinity()
                        },
                        onOpenRoutineSuggestion = { suggestion ->
                            startActivity(createPostBlockRoutinePrefillIntent(suggestion))
                            finishAffinity()
                        }
                    )
                }
            }
        }
    }

    private fun createPostBlockRoutinePrefillIntent(suggestion: RepeatBlockRoutineSuggestion): Intent =
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REPEAT_BLOCK_SURFACE, RepeatBlockRoutineSuggestionSurface.POST_BLOCK_SUCCESS)
            putExtra(EXTRA_REPEAT_BLOCK_REASON, suggestion.reason.analyticsValue)
            putExtra(EXTRA_REPEAT_BLOCK_TIME_BUCKET, suggestion.timeBucket.analyticsValue)
            putExtra(EXTRA_REPEAT_BLOCK_DAY_TYPE, suggestion.dayType.analyticsValue)
            putExtra(EXTRA_REPEAT_BLOCK_CATEGORY_BUCKET, suggestion.categoryBucket.analyticsValue)
            putExtra(EXTRA_REPEAT_BLOCK_COUNT_BUCKET, suggestion.repeatCountBucket.analyticsValue)
            putExtra(EXTRA_REPEAT_BLOCK_COVERAGE_STATE, suggestion.routineCoverageState.analyticsValue)
            putStringArrayListExtra(EXTRA_REPEAT_BLOCK_PREFILL_PACKAGES, ArrayList(suggestion.prefillPackages))
            putExtra(EXTRA_REPEAT_BLOCK_PREFILL_START_HOUR, suggestion.prefillStartTime.hour)
            putExtra(EXTRA_REPEAT_BLOCK_PREFILL_START_MINUTE, suggestion.prefillStartTime.minute)
            putExtra(EXTRA_REPEAT_BLOCK_PREFILL_END_HOUR, suggestion.prefillEndTime.hour)
            putExtra(EXTRA_REPEAT_BLOCK_PREFILL_END_MINUTE, suggestion.prefillEndTime.minute)
        }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_BLOCK_SOURCE = "block_source"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_GOAL_LOCK_ID = "goal_lock_id"
    }
}

internal data class BlockActivityArgs(
    val packageName: String,
    val blockSource: String,
    val routineId: String?,
    val goalLockId: String?,
    val lockScreenEntry: LockScreenEntry,
)

internal fun createBlockActivityArgs(
    packageName: String?,
    blockSource: String?,
    rawRoutineId: Any?,
    rawGoalLockId: Any?,
): BlockActivityArgs {
    val normalizedPackageName = packageName ?: ""
    val normalizedBlockSource = blockSource.orDefaultBlockSource()
    val normalizedRoutineId = normalizeRoutineIdExtra(rawRoutineId)
    val normalizedGoalLockId = normalizeRoutineIdExtra(rawGoalLockId)
    val lockScreenEntry = LockScreenEntry.fromBlockActivity(
        packageName = normalizedPackageName,
        blockSource = normalizedBlockSource,
        routineId = normalizedRoutineId,
        goalLockId = normalizedGoalLockId,
    )
    return BlockActivityArgs(
        packageName = normalizedPackageName,
        blockSource = lockScreenEntry.blockSource,
        routineId = lockScreenEntry.routineId,
        goalLockId = lockScreenEntry.goalLockId,
        lockScreenEntry = lockScreenEntry,
    )
}

internal fun normalizeRoutineIdExtra(rawRoutineId: Any?): String? =
    when (rawRoutineId) {
        is String -> rawRoutineId.trim().takeIf { it.isNotEmpty() }
        is Number -> rawRoutineId.toLong().toString()
        else -> null
    }

@Suppress("DEPRECATION")
private fun Bundle.getCompat(key: String): Any? = get(key)
