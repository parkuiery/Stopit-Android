package com.uiery.keep.analytics

import com.uiery.keep.BuildConfig

internal enum class AdPlacement(
    val analyticsPlacement: String,
    val adUnitId: String,
) {
    BlockTop(
        analyticsPlacement = "block_top",
        adUnitId = BuildConfig.ADMOB_BLOCK_TOP_AD_UNIT_ID,
    ),
    HomeBottom(
        analyticsPlacement = "home_bottom",
        adUnitId = BuildConfig.ADMOB_HOME_BOTTOM_AD_UNIT_ID,
    ),
    LockBottom(
        analyticsPlacement = "lock_bottom",
        adUnitId = BuildConfig.ADMOB_LOCK_BOTTOM_AD_UNIT_ID,
    ),
    MenuBottom(
        analyticsPlacement = "menu_bottom",
        adUnitId = BuildConfig.ADMOB_MENU_BOTTOM_AD_UNIT_ID,
    ),
    RoutineListBottom(
        analyticsPlacement = "routine_list_bottom",
        adUnitId = BuildConfig.ADMOB_ROUTINE_LIST_BOTTOM_AD_UNIT_ID,
    ),
    RoutineEmptyBottom(
        analyticsPlacement = "routine_empty_bottom",
        adUnitId = BuildConfig.ADMOB_ROUTINE_EMPTY_BOTTOM_AD_UNIT_ID,
    );
}

internal fun AdPlacement.toMetadata(
    screenName: String,
    screenContext: String,
): AdPlacementMetadata = AdPlacementMetadata(
    screenName = screenName,
    screenContext = screenContext,
    placement = analyticsPlacement,
    adUnitId = adUnitId,
)
