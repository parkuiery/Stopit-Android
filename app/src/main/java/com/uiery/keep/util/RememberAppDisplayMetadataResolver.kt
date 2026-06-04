package com.uiery.keep.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberAppDisplayMetadataResolver(): AppDisplayMetadataResolver {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        AppDisplayMetadataResolver(context.applicationContext.packageManager)
    }
}
