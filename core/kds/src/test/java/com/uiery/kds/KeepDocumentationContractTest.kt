package com.uiery.kds

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepDocumentationContractTest {

    private val componentDocs = mapOf(
        "KeepAlertDialog.kt" to "alert-dialog.md",
        "KeepBadge.kt" to "badge.md",
        "KeepButton.kt" to "button.md",
        "KeepCard.kt" to "card.md",
        "KeepCheckbox.kt" to "checkbox.md",
        "KeepChip.kt" to "chip.md",
        "KeepDateTimePicker.kt" to "date-time-picker.md",
        "KeepDialog.kt" to "dialog.md",
        "KeepDivider.kt" to "divider.md",
        "KeepIconButton.kt" to "icon-button.md",
        "KeepInputButton.kt" to "input-button.md",
        "KeepLabel.kt" to "label.md",
        "KeepMenu.kt" to "menu.md",
        "KeepModalBottomSheet.kt" to "modal-bottom-sheet.md",
        "KeepProgressIndicator.kt" to "progress-indicator.md",
        "KeepRadioButton.kt" to "radio-button.md",
        "KeepSegmentedControl.kt" to "segmented-control.md",
        "KeepSelectableCard.kt" to "selectable-card.md",
        "KeepSnackBar.kt" to "snackbar.md",
        "KeepSnackbarHost.kt" to "snackbar-host.md",
        "KeepSwitch.kt" to "switch.md",
        "KeepTextButton.kt" to "text-button.md",
        "KeepTextField.kt" to "text-field.md",
        "KeepTopAppBar.kt" to "top-app-bar.md",
    )

    @Test
    fun `every KDS component source has an actionable guide`() {
        val sourceDirectory = File("src/main/java/com/uiery/kds")
        val docsDirectory = File("docs/components")
        val sourceFiles = sourceDirectory
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith("Keep") && it.extension == "kt" }
            .map { it.name }
            .toSet()

        assertTrue(
            "Documentation map is missing: ${sourceFiles - componentDocs.keys}",
            componentDocs.keys.containsAll(sourceFiles),
        )

        componentDocs.forEach { (sourceName, docName) ->
            val guide = docsDirectory.resolve(docName)
            assertTrue("$sourceName requires $docName", guide.isFile)

            val content = guide.readText()
            assertTrue("$docName must name $sourceName", content.contains("Source: `$sourceName`"))
            RequiredSections.forEach { section ->
                assertTrue("$docName is missing $section", content.contains("## $section"))
            }
            assertTrue(
                "$docName must link an official SEED source",
                content.contains("https://seed-design.io/"),
            )
        }
    }

    @Test
    fun `component index links every guide`() {
        val index = File("docs/components/README.md").readText()

        componentDocs.forEach { (sourceName, docName) ->
            assertTrue("Index is missing $sourceName", index.contains("`$sourceName`"))
            assertTrue("Index is missing $docName", index.contains("($docName)"))
        }
    }

    private companion object {
        val RequiredSections = listOf(
            "SEED references",
            "Usage",
            "Anatomy",
            "Properties and states",
            "StopIt adaptation",
            "Accessibility",
            "Verification",
        )
    }
}
