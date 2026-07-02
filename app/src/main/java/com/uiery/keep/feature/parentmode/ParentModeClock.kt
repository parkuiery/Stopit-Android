package com.uiery.keep.feature.parentmode

import javax.inject.Inject

internal open class ParentModeClock @Inject constructor() {
    open fun nowMillis(): Long = System.currentTimeMillis()
}
