package com.uiery.keep.feature.history

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.uiery.keep.feature.lockhistory.LockHistoryRoute

/**
 * Legacy history callers are intentionally routed to the canonical lock-history surface.
 * The top-level app graph no longer registers a separate History route.
 */
fun NavController.navigateToHistory(
    navOptions: NavOptions? = null,
) = navigate(
    route = LockHistoryRoute,
    navOptions = navOptions,
)
