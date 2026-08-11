package com.uiery.keep.feature.lockhistory

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockHistoryVisualContractTest {
    private val sourceFiles = listOf(
        "src/main/java/com/uiery/keep/feature/lockhistory/LockHistoryScreen.kt",
        "src/main/java/com/uiery/keep/feature/lockhistory/component/LockHistorySessionItem.kt",
        "src/main/java/com/uiery/keep/feature/lockhistory/component/LockHistorySummaryCard.kt",
        "src/main/java/com/uiery/keep/feature/lockhistory/component/LockHistoryTopApps.kt",
        "src/main/java/com/uiery/keep/feature/lockhistory/component/LockHistoryWeekCalendar.kt",
        "src/main/java/com/uiery/keep/feature/lockhistory/blockedapps/BlockedAppsScreen.kt",
    )

    @Test
    fun readableTextUsesSemanticForegroundHierarchy() {
        sourceFiles.forEach { path ->
            val source = File(path).readText()

            assertTrue(
                "$path should use semantic foreground colors",
                source.contains("semanticColors.foreground.neutral") ||
                    source.contains("semanticColors.foreground.muted"),
            )
            assertFalse(
                "$path should not use the low-contrast legacy metadata color",
                source.contains("colors.onTertiaryContainer"),
            )
            assertFalse(
                "$path should not use a background token as a text color",
                source.contains("color = KeepTheme.colors.surface,"),
            )
        }
    }
}
