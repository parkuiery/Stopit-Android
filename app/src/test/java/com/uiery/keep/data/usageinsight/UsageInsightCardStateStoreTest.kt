package com.uiery.keep.data.usageinsight

import com.uiery.keep.domain.usageinsight.UsageInsightType
import com.uiery.keep.feature.review.FakeDataStore
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageInsightCardStateStoreTest {

    @Test
    fun `dismiss 후 7일간 해당 타입 억제`() = runBlocking {
        val store = createStore()
        store.recordDismissed(UsageInsightType.NightOwl, dismissedOn = LocalDate.of(2026, 7, 3))
        assertEquals(
            setOf(UsageInsightType.NightOwl),
            store.suppressedTypes(today = LocalDate.of(2026, 7, 10)),
        )
    }

    @Test
    fun `7일 경과하면 억제 해제`() = runBlocking {
        val store = createStore()
        store.recordDismissed(UsageInsightType.NightOwl, dismissedOn = LocalDate.of(2026, 7, 3))
        assertEquals(emptySet<UsageInsightType>(), store.suppressedTypes(today = LocalDate.of(2026, 7, 11)))
    }

    @Test
    fun `권한 카드 dismiss도 7일 억제`() = runBlocking {
        val store = createStore()
        store.recordPermissionCardDismissed(dismissedOn = LocalDate.of(2026, 7, 3))
        assertTrue(store.isPermissionCardSuppressed(today = LocalDate.of(2026, 7, 10)))
        assertFalse(store.isPermissionCardSuppressed(today = LocalDate.of(2026, 7, 11)))
    }

    private fun createStore(): UsageInsightCardStateStore = UsageInsightCardStateStore(FakeDataStore())
}
