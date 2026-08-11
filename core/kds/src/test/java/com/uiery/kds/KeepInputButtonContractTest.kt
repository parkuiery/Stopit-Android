package com.uiery.kds

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class KeepInputButtonContractTest {

    @Test
    fun `large input button follows SEED mobile dimensions`() {
        val dimensions = keepInputButtonDimensions()

        assertEquals(52.dp, dimensions.height)
        assertEquals(16.dp, dimensions.horizontalPadding)
        assertEquals(12.dp, dimensions.cornerRadius)
        assertEquals(1.dp, dimensions.strokeWidth)
    }
}
