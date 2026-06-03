package com.uiery.keep.feature.devtool

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.keep.R
import com.uiery.keep.feature.devtool.component.DevToolItem
import com.uiery.keep.util.deviceId
import org.orbitmvi.orbit.compose.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolScreen(
    modifier: Modifier = Modifier,
    viewModel: DevToolViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = stringResource(R.string.cd_navigate_back),
                            tint = Color(0xFFFE9E0B),
                        )
                    }
                },
                title = { }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            DevToolItem(
                title = "Device ID",
                content = deviceId(),
            )
            DevToolItem(
                title = "FCM Token",
                content = uiState.fcmToken,
            )
        }
    }
}