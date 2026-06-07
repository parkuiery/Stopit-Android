package com.uiery.keep.feature.home

import com.uiery.keep.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStatusCtaReadModelTest {
    @Test
    fun noSelectedAppsPrioritizesAppSelectionAsPrimaryCta() {
        val model = buildHomeStatusCtaModel(
            isKeep = false,
            selectedAppCount = 0,
            showFirstLockActivationCta = false,
            hasGoalLockCard = false,
        )

        assertEquals(HomeStatusKind.NO_SELECTED_APPS, model.statusKind)
        assertEquals(R.string.home_status_no_selected_apps_title, model.titleResId)
        assertEquals(R.string.home_status_no_selected_apps_description, model.descriptionResId)
        assertEquals(R.string.home_primary_cta_select_apps, model.primaryCtaResId)
        assertTrue(model.shouldOpenAppSelection)
        assertFalse(model.shouldToggleKeep)
        assertFalse(model.timerEnabled)
    }

    @Test
    fun firstLockReadyPrioritizesImmediateLockAndKeepsTimerSecondary() {
        val model = buildHomeStatusCtaModel(
            isKeep = false,
            selectedAppCount = 3,
            showFirstLockActivationCta = true,
            hasGoalLockCard = false,
        )

        assertEquals(HomeStatusKind.FIRST_LOCK_READY, model.statusKind)
        assertEquals(R.string.home_status_first_lock_ready_title, model.titleResId)
        assertEquals(R.string.home_status_first_lock_ready_description, model.descriptionResId)
        assertEquals(R.string.home_primary_cta_start_now, model.primaryCtaResId)
        assertTrue(model.shouldToggleKeep)
        assertTrue(model.timerEnabled)
        assertTrue(model.showChangeAppsSecondary)
    }

    @Test
    fun activeKeepPresentsStatusBeforeSecondaryActions() {
        val model = buildHomeStatusCtaModel(
            isKeep = true,
            selectedAppCount = 2,
            showFirstLockActivationCta = false,
            hasGoalLockCard = false,
        )

        assertEquals(HomeStatusKind.KEEP_ACTIVE, model.statusKind)
        assertEquals(R.string.home_status_keep_active_title, model.titleResId)
        assertEquals(R.string.home_status_keep_active_description, model.descriptionResId)
        assertEquals(R.string.home_primary_status_keep_active, model.primaryCtaResId)
        assertFalse(model.shouldToggleKeep)
        assertFalse(model.timerEnabled)
        assertTrue(model.showLockHistorySecondary)
    }

    @Test
    fun goalLockPresenceKeepsGoalCardVisibleWithoutStealingFirstLockPrimaryCta() {
        val model = buildHomeStatusCtaModel(
            isKeep = false,
            selectedAppCount = 1,
            showFirstLockActivationCta = true,
            hasGoalLockCard = true,
        )

        assertEquals(HomeStatusKind.FIRST_LOCK_READY, model.statusKind)
        assertEquals(R.string.home_primary_cta_start_now, model.primaryCtaResId)
        assertTrue(model.showGoalLockStatus)
        assertTrue(model.shouldToggleKeep)
    }
}
