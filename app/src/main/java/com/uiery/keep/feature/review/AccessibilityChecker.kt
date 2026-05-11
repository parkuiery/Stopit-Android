package com.uiery.keep.feature.review

import android.content.Context
import com.uiery.keep.util.hasAccessibilityPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface AccessibilityChecker {
    fun isEnabled(): Boolean
}

@Singleton
class AndroidAccessibilityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : AccessibilityChecker {
    override fun isEnabled(): Boolean = hasAccessibilityPermission(context)
}
