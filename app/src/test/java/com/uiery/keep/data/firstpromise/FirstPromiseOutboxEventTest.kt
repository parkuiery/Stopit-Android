package com.uiery.keep.data.firstpromise

import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.domain.firstpromise.FirstPromiseScheduleState
import com.uiery.keep.domain.firstpromise.FirstPromiseSource
import com.uiery.keep.domain.firstpromise.FirstPromiseOrigin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstPromiseOutboxEventTest {
    private val codec = FirstPromiseOutboxEventCodec()

    @Test
    fun fixedCreationEventsRoundTripWithCanonicalNamesAndSequences() {
        val events = listOf(
            FirstPromiseOutboxEvent.RoutineSaved(
                repeatDaysBucket = FirstPromiseRepeatDaysBucket.Seven,
                timeWindowBucket = FirstPromiseTimeWindowBucket.Night,
                scheduleState = FirstPromiseScheduleState.Enabled,
            ),
            FirstPromiseOutboxEvent.FirstPromiseCreated(
                goal = FirstPromiseGoal.Focus,
                source = FirstPromiseSource.Personalized,
                scheduleState = FirstPromiseScheduleState.Enabled,
            ),
        )

        val entities = events.map { codec.encode("local-draft", it, 100L) }

        assertEquals(listOf(10, 20), entities.map { it.sequence })
        assertEquals(listOf("routine_saved", "first_promise_created"), entities.map { it.canonicalEventName })
        assertEquals(events, entities.map(codec::decode))
    }

    @Test
    fun encodedPayloadContainsOnlyApprovedBucketsAndNeverLocalOrObservedFacts() {
        val entity = codec.encode(
            draftId = "draft-secret",
            event = FirstPromiseOutboxEvent.FirstPromiseCreated(
                goal = FirstPromiseGoal.Sleep,
                source = FirstPromiseSource.GoalTemplate,
                scheduleState = FirstPromiseScheduleState.DisabledExactAlarmMissing,
            ),
            occurredAtMillis = 123L,
        )

        val payload = Json.parseToJsonElement(entity.payloadJson).jsonObject
        assertEquals(setOf("goal_type", "source", "schedule_state"), payload.keys)
        val serialized = entity.payloadJson.lowercase()
        listOf("draft-secret", "package", "app_label", "routine_id", "draft_id", "observed", "minutes").forEach {
            assertFalse(serialized.contains(it))
        }
    }

    @Test
    fun unknownOrMismatchedSequenceIsRejected() {
        val valid = codec.encode(
            draftId = "draft",
            event = FirstPromiseOutboxEvent.FirstPromiseCreated(
                FirstPromiseGoal.Study,
                FirstPromiseSource.Manual,
                FirstPromiseScheduleState.Enabled,
            ),
            occurredAtMillis = 1L,
        )

        assertNull(codec.decodeOrNull(valid.copy(sequence = 10)))
        assertNull(codec.decodeOrNull(valid.copy(eventName = "future_event")))
        assertTrue(codec.decodeOrNull(valid) is FirstPromiseOutboxEvent.FirstPromiseCreated)
    }

    @Test
    fun valueEventsUseFixedThirtyFortySequencesAndPrivacySafeAllowlist() {
        val intercepted = FirstPromiseOutboxEvent.AppBlockIntercepted(
            blockSource = FirstPromiseBlockSource.TimedLock,
            blockingMode = FirstPromiseBlockingMode.TimedLock,
            categoryBucket = FirstPromiseAppCategoryBucket.Productivity,
            promiseOrigin = FirstPromiseOrigin.FirstPromisePractice,
        )
        val core = FirstPromiseOutboxEvent.CoreAction(
            kind = FirstPromiseCoreActionKind.First,
            blockingMode = FirstPromiseBlockingMode.TimedLock,
            categoryBucket = FirstPromiseAppCategoryBucket.Productivity,
            elapsedBucket = FirstPromiseElapsedSinceOpenBucket.UnderMinute,
            promiseOrigin = FirstPromiseOrigin.FirstPromisePractice,
        )

        val interceptedEntity = codec.encode("secret-draft", intercepted, 1L)
        val coreEntity = codec.encode("secret-draft", core, 2L)

        assertEquals(listOf(30, 40), listOf(interceptedEntity.sequence, coreEntity.sequence))
        assertEquals(intercepted, codec.decode(interceptedEntity))
        assertEquals(core, codec.decode(coreEntity))
        assertEquals(
            setOf("block_source", "blocking_mode", "blocked_app_category_bucket", "promise_origin"),
            Json.parseToJsonElement(interceptedEntity.payloadJson).jsonObject.keys,
        )
        assertFalse((interceptedEntity.payloadJson + coreEntity.payloadJson).contains("secret-draft"))
    }
}
