package com.uiery.keep.data.usageinsight

import com.uiery.keep.data.routine.RoutineRepository
import com.uiery.keep.domain.usageinsight.EnabledRoutineCoverage
import com.uiery.keep.domain.usageinsight.InsufficientReason
import com.uiery.keep.domain.usageinsight.OnboardingUsageAggregate
import com.uiery.keep.domain.usageinsight.OnboardingUsageInterval
import com.uiery.keep.domain.usageinsight.OnboardingUsageProfilePolicy
import com.uiery.keep.domain.usageinsight.OnboardingUsageProfileResult
import com.uiery.keep.util.toDayOfWeekList
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CancellationException
import javax.inject.Inject

class OnboardingUsageProfileRepository @Inject constructor(
    private val gateway: UsageStatsGateway,
    private val routineRepository: RoutineRepository,
) {
    suspend fun profile(
        today: LocalDate,
        zoneId: ZoneId,
        goalDefaultStartMinutes: Int,
    ): OnboardingUsageProfileResult = try {
        queryProfile(today, zoneId, goalDefaultStartMinutes)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        queryFailed()
    }

    private suspend fun queryProfile(
        today: LocalDate,
        zoneId: ZoneId,
        goalDefaultStartMinutes: Int,
    ): OnboardingUsageProfileResult {
        val days = today.minusDays(7)..today.minusDays(1)
        val aggregates = gateway.queryOnboardingDailyAggregates(days, zoneId)
        val intervals = gateway.queryOnboardingUsageIntervals(days, zoneId)
        val appLabels = aggregates
            .map { it.packageName }
            .distinct()
            .associateWith(gateway::appLabel)
        val enabledRoutines = routineRepository.fetchAllOnce()
            .asSequence()
            .filter { it.isEnabled }
            .map { routine ->
                EnabledRoutineCoverage(
                    packageNames = routine.lockApplications.orEmpty().toSet(),
                    startMinutes = routine.startTime.hour * 60 + routine.startTime.minute,
                    endMinutes = routine.endTime.hour * 60 + routine.endTime.minute,
                    repeatDays = routine.repeatDays.toDayOfWeekList().map { it.value }.toSet(),
                )
            }
            .toList()
        return OnboardingUsageProfilePolicy.evaluate(
            aggregates = aggregates.map { aggregate ->
                OnboardingUsageAggregate(
                    packageName = aggregate.packageName,
                    totalForegroundMillis = aggregate.totalForegroundMillis,
                    lastUsedEpochMillis = aggregate.lastUsedEpochMillis,
                    localDate = aggregate.localDate,
                )
            },
            intervals = intervals.map { interval ->
                OnboardingUsageInterval(
                    packageName = interval.packageName,
                    startMillis = interval.startMillis,
                    endMillis = interval.endMillis,
                    localDate = interval.localDate,
                )
            },
            appLabels = appLabels.filterValues { it != null }.mapValues { it.value.orEmpty() },
            enabledRoutines = enabledRoutines,
            goalDefaultStartMinutes = goalDefaultStartMinutes,
            proposedRepeatDays = ALL_REPEAT_DAYS,
            zoneId = zoneId,
        )
    }

    private fun queryFailed() = OnboardingUsageProfileResult.Insufficient(
        usageCoverageDays = 0,
        eventCoverageDays = 0,
        reason = InsufficientReason.QueryFailed,
    )

    private companion object {
        val ALL_REPEAT_DAYS = (1..7).toSet()
    }
}
