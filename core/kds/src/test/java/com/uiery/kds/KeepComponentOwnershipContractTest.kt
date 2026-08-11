package com.uiery.kds

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepComponentOwnershipContractTest {

    @Test
    fun sharedComponentFamiliesExistInKds() {
        val sourceRoot = File("src/main/java/com/uiery/kds")
        val requiredFiles = listOf(
            "KeepAlertDialog.kt",
            "KeepBadge.kt",
            "KeepCard.kt",
            "KeepChip.kt",
            "KeepDialog.kt",
            "KeepLabel.kt",
            "KeepProgressIndicator.kt",
            "KeepSegmentedControl.kt",
            "KeepSelectableCard.kt",
        )

        requiredFiles.forEach { fileName ->
            assertTrue("$fileName must be owned by KDS", sourceRoot.resolve(fileName).isFile)
        }
    }

    @Test
    fun kdsDoesNotDependOnAppPackages() {
        val violations = File("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readText().contains("import com.uiery.keep.") }
            .toList()

        assertFalse("KDS must remain app-agnostic: $violations", violations.isNotEmpty())
    }
}
