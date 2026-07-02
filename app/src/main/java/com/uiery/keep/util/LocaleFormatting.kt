package com.uiery.keep.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

fun formatWeekdayShort(
    dayOfWeek: DayOfWeek,
    locale: Locale,
): String = dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

fun formatMonthDayLabel(
    date: LocalDate,
    locale: Locale,
): String = "${date.month.getDisplayName(TextStyle.FULL, locale)} ${date.dayOfMonth}"
