package com.uiery.keep

import org.junit.Assert.assertEquals
import org.junit.Test

class PickerContractTest {

    @Test
    fun pickerListStartIndexRecomputesWhenStartIndexChanges() {
        assertEquals(
            1_073_741_817,
            pickerListStartIndex(
                itemsSize = 12,
                startIndex = 0,
                visibleItemsCount = 7,
                isInfinity = true,
            ),
        )
        assertEquals(
            1_073_741_822,
            pickerListStartIndex(
                itemsSize = 12,
                startIndex = 5,
                visibleItemsCount = 7,
                isInfinity = true,
            ),
        )
    }
}
