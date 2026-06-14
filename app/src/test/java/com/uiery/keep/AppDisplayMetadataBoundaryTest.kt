package com.uiery.keep

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class AppDisplayMetadataBoundaryTest {

    @Test
    fun knownPackageDisplaySurfacesDoNotConstructResolversOrReadPackageManagerDirectly() {
        displaySurfaceFiles.forEach { relativePath ->
            val source = File(relativePath).readText()

            assertFalse(
                "$relativePath should use the shared rememberAppDisplayMetadataResolver boundary",
                Regex("[^A-Za-z0-9_]AppDisplayMetadataResolver\\(").containsMatchIn(source)
            )
            assertFalse(
                "$relativePath should not read PackageManager directly for app display metadata",
                source.contains(".packageManager") || source.contains("val packageManager") || source.contains("val pm =")
            )
        }
    }

    private companion object {
        val displaySurfaceFiles = listOf(
            "src/main/java/com/uiery/keep/BlockScreen.kt",
            "src/main/java/com/uiery/keep/feature/goallock/GoalLockCreationScreen.kt",
            "src/main/java/com/uiery/keep/ui/component/EmergencyUnlockBottomSheetContent.kt",
            "src/main/java/com/uiery/keep/feature/lockhistory/blockedapps/BlockedAppsScreen.kt",
            "src/main/java/com/uiery/keep/feature/lockhistory/component/LockHistoryTopApps.kt",
        )
    }
}
