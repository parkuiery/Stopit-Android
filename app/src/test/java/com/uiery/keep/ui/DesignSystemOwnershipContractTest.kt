package com.uiery.keep.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemOwnershipContractTest {

    @Test
    fun appDoesNotImportMaterialVisualComponentsOwnedByKds() {
        val forbidden = setOf(
            "AlertDialog",
            "Button",
            "Card",
            "CenterAlignedTopAppBar",
            "Checkbox",
            "CircularProgressIndicator",
            "DatePicker",
            "DatePickerDialog",
            "FilterChip",
            "HorizontalDivider",
            "IconButton",
            "LinearProgressIndicator",
            "ModalBottomSheet",
            "OutlinedButton",
            "OutlinedTextField",
            "RadioButton",
            "SnackbarHost",
            "Switch",
            "TextButton",
            "TextField",
            "TimeInput",
            "TopAppBar",
        )
        val sourceRoot = File("src/main/java")
        val violations = sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .filter { line ->
                        line.startsWith("import androidx.compose.material3.") &&
                            line.substringAfterLast('.').substringBefore(" as ") in forbidden
                    }
                    .map { line -> "${file.relativeTo(sourceRoot)}: $line" }
            }
            .toList()

        assertTrue(
            "Material visual components must be consumed through KDS:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun appDoesNotImportComposeDialogOwnedByKds() {
        val sourceRoot = File("src/main/java")
        val violations = sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .filter { line -> line == "import androidx.compose.ui.window.Dialog" }
                    .map { line -> "${file.relativeTo(sourceRoot)}: $line" }
            }
            .toList()

        assertTrue(
            "Dialog containers must be consumed through KDS:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun appDoesNotDecorateFeatureLocalTextInputs() {
        val sourceRoot = File("src/main/java")
        val violations = sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .filter { line ->
                        line == "import androidx.compose.foundation.text.BasicTextField"
                    }
                    .map { line -> "${file.relativeTo(sourceRoot)}: $line" }
            }
            .toList()

        assertTrue(
            "Text input decoration must be consumed through KDS:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }
}
