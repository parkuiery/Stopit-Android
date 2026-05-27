package com.uiery.keep

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class OverlayContractCleanupTest {
    @Test
    fun manifestDoesNotDeclareOverlayPermissions() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertFalse(manifest.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertFalse(manifest.contains("android.permission.HIDE_OVERLAY_WINDOWS"))
    }

    @Test
    fun blockFlowDoesNotKeepDeadOverlayImplementation() {
        assertFalse(File("src/main/java/com/uiery/keep/PipBlockerOverlay.kt").exists())

        val blockActivity = File("src/main/java/com/uiery/keep/BlockActivity.kt").readText()
        assertFalse(blockActivity.contains("PipBlockerOverlay"))
        assertFalse(blockActivity.contains("pipBlocker"))
    }
}
