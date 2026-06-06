package com.uiery.keep.feature.goallock

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalLockSelectedAppUiItemTest {
    @Test
    fun selectedAppItemsUseResolvedLabelsAndStablePackageForRemoval() {
        val items = buildGoalLockSelectedAppItems(
            selectedPackages = setOf("com.video.app", "com.social.app", "com.unknown.app"),
            resolveLabel = { packageName ->
                when (packageName) {
                    "com.video.app" -> "Video"
                    "com.social.app" -> "Social"
                    else -> null
                }
            },
        )

        assertEquals(
            listOf(
                GoalLockSelectedAppUiItem(
                    packageName = "com.social.app",
                    label = "Social",
                    description = "com.social.app",
                ),
                GoalLockSelectedAppUiItem(
                    packageName = "com.video.app",
                    label = "Video",
                    description = "com.video.app",
                ),
                GoalLockSelectedAppUiItem(
                    packageName = "com.unknown.app",
                    label = "com.unknown.app",
                    description = "앱 이름을 불러오지 못했어요",
                ),
            ),
            items,
        )
    }
}
