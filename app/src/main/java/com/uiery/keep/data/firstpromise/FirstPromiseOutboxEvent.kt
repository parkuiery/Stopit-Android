package com.uiery.keep.data.firstpromise

import com.uiery.keep.analytics.AnalyticsBlockSource
import com.uiery.keep.analytics.KeepAnalyticsEvent
import com.uiery.keep.analytics.KeepAnalyticsParam
import com.uiery.keep.analytics.routine.RoutineAnalyticsEvent
import com.uiery.keep.database.entity.FirstPromiseAnalyticsOutboxEntity
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseOrigin
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface FirstPromiseOutboxEvent {
    val sequence: Int
    val localEventName: String
    val canonicalEventName: String

    data class RoutineSaved(
        val repeatDaysBucket: FirstPromiseRepeatDaysBucket,
        val timeWindowBucket: FirstPromiseTimeWindowBucket,
        val scheduleState: FirstPromiseScheduleState,
    ) : FirstPromiseOutboxEvent {
        override val sequence = 10
        override val localEventName = "routine_saved"
        override val canonicalEventName = RoutineAnalyticsEvent.ROUTINE_SAVED
    }

    data class FirstPromiseCreated(
        val goal: FirstPromiseGoal,
        val source: FirstPromiseSource,
        val scheduleState: FirstPromiseScheduleState,
    ) : FirstPromiseOutboxEvent {
        override val sequence = 20
        override val localEventName = "first_promise_created"
        override val canonicalEventName = KeepAnalyticsEvent.FIRST_PROMISE_CREATED
    }

    data class AppBlockIntercepted(
        val blockSource: FirstPromiseBlockSource,
        val blockingMode: FirstPromiseBlockingMode,
        val categoryBucket: FirstPromiseAppCategoryBucket,
        val promiseOrigin: FirstPromiseOrigin,
    ) : FirstPromiseOutboxEvent {
        override val sequence = 30
        override val localEventName = "first_promise_value_intercepted"
        override val canonicalEventName = KeepAnalyticsEvent.APP_BLOCK_INTERCEPTED
    }

    data class CoreAction(
        val kind: FirstPromiseCoreActionKind,
        val blockingMode: FirstPromiseBlockingMode,
        val categoryBucket: FirstPromiseAppCategoryBucket,
        val elapsedBucket: FirstPromiseElapsedSinceOpenBucket,
        val promiseOrigin: FirstPromiseOrigin,
    ) : FirstPromiseOutboxEvent {
        override val sequence = 40
        override val localEventName = "first_promise_core_action"
        override val canonicalEventName = when (kind) {
            FirstPromiseCoreActionKind.First -> KeepAnalyticsEvent.FIRST_CORE_ACTION_COMPLETED
            FirstPromiseCoreActionKind.Repeat -> KeepAnalyticsEvent.CORE_ACTION_COMPLETED
        }
    }
}

enum class FirstPromiseRepeatDaysBucket(val analyticsValue: String) {
    One("1"), TwoThree("2_3"), FourSix("4_6"), Seven("7"),
}

enum class FirstPromiseTimeWindowBucket(val analyticsValue: String) {
    Morning("morning"), Afternoon("afternoon"), Evening("evening"), Night("night"), Overnight("overnight"),
}

enum class FirstPromiseBlockSource(val analyticsValue: String) {
    TimedLock(AnalyticsBlockSource.TIMED_LOCK), Routine(AnalyticsBlockSource.ROUTINE),
}

enum class FirstPromiseBlockingMode(val analyticsValue: String) {
    TimedLock(AnalyticsBlockSource.TIMED_LOCK), Routine(AnalyticsBlockSource.ROUTINE),
}

enum class FirstPromiseAppCategoryBucket(val analyticsValue: String) {
    Social("social"), Video("video"), Game("game"), Communication("communication"),
    Shopping("shopping"), Browser("browser"), Productivity("productivity"), Unknown("unknown"),
}

enum class FirstPromiseCoreActionKind { First, Repeat }

enum class FirstPromiseElapsedSinceOpenBucket(val analyticsValue: String) {
    UnderMinute("under_1m"),
    OneToFiveMinutes("1_5m"),
    OverFiveMinutes("over_5m"),
    ;

    companion object {
        fun fromElapsedSeconds(elapsedSeconds: Long): FirstPromiseElapsedSinceOpenBucket {
            require(elapsedSeconds >= 0L) { "elapsedSeconds must be non-negative" }
            return when {
                elapsedSeconds <= 59L -> UnderMinute
                elapsedSeconds <= 300L -> OneToFiveMinutes
                else -> OverFiveMinutes
            }
        }
    }
}

class FirstPromiseOutboxEventCodec @Inject constructor() {
    fun encode(
        draftId: String,
        event: FirstPromiseOutboxEvent,
        occurredAtMillis: Long,
    ): FirstPromiseAnalyticsOutboxEntity = FirstPromiseAnalyticsOutboxEntity(
        draftId = draftId,
        eventName = event.localEventName,
        sequence = event.sequence,
        canonicalEventName = event.canonicalEventName,
        payloadJson = JsonObject(event.toPayload()).toString(),
        occurredAtMillis = occurredAtMillis,
        deliveryState = DELIVERY_PENDING,
    )

    fun decode(entity: FirstPromiseAnalyticsOutboxEntity): FirstPromiseOutboxEvent =
        requireNotNull(decodeOrNull(entity)) { "Unknown or invalid first-promise outbox event" }

    fun decodeOrNull(entity: FirstPromiseAnalyticsOutboxEntity): FirstPromiseOutboxEvent? = runCatching {
        val payload = Json.parseToJsonElement(entity.payloadJson).jsonObject
        val event = when (entity.eventName) {
            "routine_saved" -> {
                requireExactKeys(payload, ROUTINE_SAVED_KEYS)
                check(payload.stringValue("entry_surface") == "onboarding")
                check(payload.stringValue("creation_source") == "onboarding_promise")
                check(payload.stringValue("selected_app_count_bucket") == "1")
                FirstPromiseOutboxEvent.RoutineSaved(
                    repeatDaysBucket = enumValue(payload, "repeat_days_bucket", FirstPromiseRepeatDaysBucket.entries, FirstPromiseRepeatDaysBucket::analyticsValue),
                    timeWindowBucket = enumValue(payload, "time_window_bucket", FirstPromiseTimeWindowBucket.entries, FirstPromiseTimeWindowBucket::analyticsValue),
                    scheduleState = enumValue(payload, "schedule_state", FirstPromiseScheduleState.entries, FirstPromiseScheduleState::analyticsValue),
                )
            }
            "first_promise_created" -> {
                requireExactKeys(payload, FIRST_PROMISE_CREATED_KEYS)
                FirstPromiseOutboxEvent.FirstPromiseCreated(
                    goal = enumValue(payload, "goal_type", FirstPromiseGoal.entries, FirstPromiseGoal::analyticsValue),
                    source = enumValue(payload, "source", FirstPromiseSource.entries, FirstPromiseSource::analyticsValue),
                    scheduleState = enumValue(payload, "schedule_state", FirstPromiseScheduleState.entries, FirstPromiseScheduleState::analyticsValue),
                )
            }
            "first_promise_value_intercepted" -> {
                requireExactKeys(payload, APP_BLOCK_INTERCEPTED_KEYS)
                FirstPromiseOutboxEvent.AppBlockIntercepted(
                    blockSource = enumValue(payload, KeepAnalyticsParam.BLOCK_SOURCE, FirstPromiseBlockSource.entries, FirstPromiseBlockSource::analyticsValue),
                    blockingMode = enumValue(payload, KeepAnalyticsParam.BLOCKING_MODE, FirstPromiseBlockingMode.entries, FirstPromiseBlockingMode::analyticsValue),
                    categoryBucket = enumValue(payload, KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET, FirstPromiseAppCategoryBucket.entries, FirstPromiseAppCategoryBucket::analyticsValue),
                    promiseOrigin = enumValue(payload, KeepAnalyticsParam.PROMISE_ORIGIN, FirstPromiseOrigin.entries, FirstPromiseOrigin::analyticsValue),
                )
            }
            "first_promise_core_action" -> {
                requireExactKeys(payload, CORE_ACTION_KEYS)
                FirstPromiseOutboxEvent.CoreAction(
                    kind = enumValueByName(payload, "core_action_kind", FirstPromiseCoreActionKind.entries),
                    blockingMode = enumValue(payload, KeepAnalyticsParam.BLOCKING_MODE, FirstPromiseBlockingMode.entries, FirstPromiseBlockingMode::analyticsValue),
                    categoryBucket = enumValue(payload, KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET, FirstPromiseAppCategoryBucket.entries, FirstPromiseAppCategoryBucket::analyticsValue),
                    elapsedBucket = enumValue(payload, KeepAnalyticsParam.ELAPSED_SINCE_FIRST_OPEN_BUCKET, FirstPromiseElapsedSinceOpenBucket.entries, FirstPromiseElapsedSinceOpenBucket::analyticsValue),
                    promiseOrigin = enumValue(payload, KeepAnalyticsParam.PROMISE_ORIGIN, FirstPromiseOrigin.entries, FirstPromiseOrigin::analyticsValue),
                )
            }
            else -> return null
        }
        check(entity.sequence == event.sequence)
        check(entity.canonicalEventName == event.canonicalEventName)
        event
    }.getOrNull()

    private fun FirstPromiseOutboxEvent.toPayload(): Map<String, JsonPrimitive> = when (this) {
        is FirstPromiseOutboxEvent.RoutineSaved -> mapOf(
            "entry_surface" to JsonPrimitive("onboarding"),
            "creation_source" to JsonPrimitive("onboarding_promise"),
            "selected_app_count_bucket" to JsonPrimitive("1"),
            "repeat_days_bucket" to JsonPrimitive(repeatDaysBucket.analyticsValue),
            "time_window_bucket" to JsonPrimitive(timeWindowBucket.analyticsValue),
            "schedule_state" to JsonPrimitive(scheduleState.analyticsValue),
        )
        is FirstPromiseOutboxEvent.FirstPromiseCreated -> mapOf(
            "goal_type" to JsonPrimitive(goal.analyticsValue),
            "source" to JsonPrimitive(source.analyticsValue),
            "schedule_state" to JsonPrimitive(scheduleState.analyticsValue),
        )
        is FirstPromiseOutboxEvent.AppBlockIntercepted -> mapOf(
            KeepAnalyticsParam.BLOCK_SOURCE to JsonPrimitive(blockSource.analyticsValue),
            KeepAnalyticsParam.BLOCKING_MODE to JsonPrimitive(blockingMode.analyticsValue),
            KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET to JsonPrimitive(categoryBucket.analyticsValue),
            KeepAnalyticsParam.PROMISE_ORIGIN to JsonPrimitive(promiseOrigin.analyticsValue),
        )
        is FirstPromiseOutboxEvent.CoreAction -> mapOf(
            "core_action_kind" to JsonPrimitive(kind.name),
            KeepAnalyticsParam.BLOCKING_MODE to JsonPrimitive(blockingMode.analyticsValue),
            KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET to JsonPrimitive(categoryBucket.analyticsValue),
            KeepAnalyticsParam.ELAPSED_SINCE_FIRST_OPEN_BUCKET to JsonPrimitive(elapsedBucket.analyticsValue),
            KeepAnalyticsParam.PROMISE_ORIGIN to JsonPrimitive(promiseOrigin.analyticsValue),
        )
    }

    private fun <T : Enum<T>> enumValue(
        payload: JsonObject,
        key: String,
        entries: List<T>,
        analyticsValue: (T) -> String,
    ): T {
        val raw = payload.getValue(key).jsonPrimitive.content
        return entries.firstOrNull { analyticsValue(it) == raw } ?: error("Unknown $key")
    }

    private fun <T : Enum<T>> enumValueByName(payload: JsonObject, key: String, entries: List<T>): T {
        val raw = payload.getValue(key).jsonPrimitive.content
        return entries.firstOrNull { it.name == raw } ?: error("Unknown $key")
    }

    private fun requireExactKeys(payload: JsonObject, approvedKeys: Set<String>) {
        check(payload.keys == approvedKeys)
    }

    private fun JsonObject.stringValue(key: String): String = getValue(key).jsonPrimitive.content

    companion object {
        const val DELIVERY_PENDING = "pending"
        const val DELIVERY_SENT = "sent"
        const val DELIVERY_QUARANTINED = "quarantined"

        private val ROUTINE_SAVED_KEYS = setOf(
            "entry_surface",
            "creation_source",
            "selected_app_count_bucket",
            "repeat_days_bucket",
            "time_window_bucket",
            "schedule_state",
        )
        private val FIRST_PROMISE_CREATED_KEYS = setOf("goal_type", "source", "schedule_state")
        private val APP_BLOCK_INTERCEPTED_KEYS = setOf(
            KeepAnalyticsParam.BLOCK_SOURCE,
            KeepAnalyticsParam.BLOCKING_MODE,
            KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET,
            KeepAnalyticsParam.PROMISE_ORIGIN,
        )
        private val CORE_ACTION_KEYS = setOf(
            "core_action_kind",
            KeepAnalyticsParam.BLOCKING_MODE,
            KeepAnalyticsParam.BLOCKED_APP_CATEGORY_BUCKET,
            KeepAnalyticsParam.ELAPSED_SINCE_FIRST_OPEN_BUCKET,
            KeepAnalyticsParam.PROMISE_ORIGIN,
        )
    }
}
