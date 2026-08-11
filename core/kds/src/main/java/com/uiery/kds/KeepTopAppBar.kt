package com.uiery.kds

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uiery.kds.theme.KeepTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = KeepTheme.semanticColors.background.layerBasement,
            scrolledContainerColor = KeepTheme.semanticColors.background.layerBasement,
            navigationIconContentColor = KeepTheme.semanticColors.foreground.neutral,
            titleContentColor = KeepTheme.semanticColors.foreground.neutral,
            actionIconContentColor = KeepTheme.semanticColors.foreground.neutral,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepCenterAlignedTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = KeepTheme.semanticColors.background.layerBasement,
            scrolledContainerColor = KeepTheme.semanticColors.background.layerBasement,
            navigationIconContentColor = KeepTheme.semanticColors.foreground.neutral,
            titleContentColor = KeepTheme.semanticColors.foreground.neutral,
            actionIconContentColor = KeepTheme.semanticColors.foreground.neutral,
        ),
    )
}
