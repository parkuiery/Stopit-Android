package com.uiery.kds

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class KeepSwitchContractTest {

    @Test
    fun `large switch follows seed switchmark dimensions`() {
        val dimensions = keepSwitchDimensions(KeepSwitchSize.Large)

        assertEquals(52.dp, dimensions.trackWidth)
        assertEquals(32.dp, dimensions.trackHeight)
        assertEquals(3.dp, dimensions.padding)
        assertEquals(26.dp, dimensions.thumbSize)
        assertEquals(48.dp, dimensions.touchTarget)
    }

    @Test
    fun `compact switches preserve seed geometry and accessible touch target`() {
        val small = keepSwitchDimensions(KeepSwitchSize.Small)
        val medium = keepSwitchDimensions(KeepSwitchSize.Medium)

        assertEquals(26.dp, small.trackWidth)
        assertEquals(16.dp, small.trackHeight)
        assertEquals(12.dp, small.thumbSize)
        assertEquals(48.dp, small.touchTarget)

        assertEquals(38.dp, medium.trackWidth)
        assertEquals(24.dp, medium.trackHeight)
        assertEquals(20.dp, medium.thumbSize)
        assertEquals(48.dp, medium.touchTarget)
    }
}
