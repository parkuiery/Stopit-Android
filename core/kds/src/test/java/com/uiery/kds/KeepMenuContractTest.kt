package com.uiery.kds

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class KeepMenuContractTest {

    @Test
    fun mediumMenuUsesSeedMobileDimensions() {
        val dimensions = keepMenuDimensions()

        assertEquals(240.dp, dimensions.width)
        assertEquals(16.dp, dimensions.cornerRadius)
        assertEquals(16.dp, dimensions.horizontalPadding)
        assertEquals(12.dp, dimensions.verticalPadding)
        assertEquals(12.dp, dimensions.contentGap)
    }
}
