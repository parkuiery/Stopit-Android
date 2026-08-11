package com.uiery.keep.database.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListStringTypeConverterTest {
    private val converter = ListStringTypeConverter()

    @Test
    fun anEmptyColumnIsAnEmptyList() {
        // lockWebsites 는 기존 행에 빈 문자열로 채워진다. `"".split(",")` 를 그대로 쓰면
        // 아무것도 고르지 않은 루틴이 "대상 한 개"로 읽혀 잠금 대상이 있는 것처럼 보인다.
        assertEquals(emptyList<String>(), converter.fromString(""))
    }

    @Test
    fun blankEntriesNeverBecomeTargets() {
        assertEquals(listOf("a.com", "b.com"), converter.fromString("a.com,,b.com"))
    }

    @Test
    fun normalValuesRoundTrip() {
        val list = listOf("com.example.alpha", "com.example.beta")
        assertEquals(list, converter.fromString(converter.fromList(list)))
    }

    @Test
    fun nullStaysNull() {
        assertNull(converter.fromString(null))
        assertNull(converter.fromList(null))
    }
}
