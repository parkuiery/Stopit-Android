package com.uiery.kds

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepButtonContractTest {

    @Test
    fun `loading indicator uses the selected button variant colors`() {
        val source = File("src/main/java/com/uiery/kds/KeepButton.kt").readText()

        assertTrue(source.contains("color = specification.contentColor"))
        assertTrue(source.contains("trackColor = specification.loadingTrackColor"))
    }
}
