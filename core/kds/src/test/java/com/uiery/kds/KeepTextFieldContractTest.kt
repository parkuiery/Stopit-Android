package com.uiery.kds

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeepTextFieldContractTest {

    @Test
    fun `outline input follows SEED mobile large dimensions`() {
        val dimensions = keepTextInputDimensions(KeepTextFieldVariant.Outline)

        assertEquals(52.dp, dimensions.minHeight)
        assertEquals(16.dp, dimensions.horizontalPadding)
        assertEquals(12.dp, dimensions.cornerRadius)
    }

    @Test
    fun `underline input follows SEED single-input dimensions`() {
        val dimensions = keepTextInputDimensions(KeepTextFieldVariant.Underline)

        assertEquals(40.dp, dimensions.minHeight)
        assertEquals(0.dp, dimensions.horizontalPadding)
        assertEquals(0.dp, dimensions.cornerRadius)
    }

    @Test
    fun `multiline input preserves SEED textarea minimum height`() {
        assertEquals(
            95.dp,
            keepTextInputMinHeight(
                variant = KeepTextFieldVariant.Outline,
                singleLine = false,
            ),
        )
    }

    @Test
    fun `field error replaces helper text`() {
        assertEquals(
            KeepFieldMessage(
                text = "10자 이내로 입력해 주세요",
                isError = true,
            ),
            resolveKeepFieldMessage(
                helperText = "루틴을 구분할 수 있는 이름",
                errorMessage = "10자 이내로 입력해 주세요",
            ),
        )
    }

    @Test
    fun `field without supporting copy has no footer message`() {
        assertNull(
            resolveKeepFieldMessage(
                helperText = null,
                errorMessage = null,
            ),
        )
    }
}
