package com.uiery.keep.feature.goallock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalLockDetailScreen(
    viewModel: GoalLockDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadGoalLock()
    }

    viewModel.collectSideEffect { effect ->
        when (effect) {
            GoalLockDetailSideEffect.NotFound,
            GoalLockDetailSideEffect.Ended,
            -> onNavigateBack()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "목표 잠금") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24),
                            contentDescription = "뒤로 가기",
                            tint = KeepTheme.colors.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KeepTheme.colors.background,
                ),
            )
        },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        GoalLockDetailContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            state = uiState,
            onRequestEnd = viewModel::requestEndGoalLock,
            onCancelEnd = viewModel::cancelEndGoalLock,
            onConfirmEnd = { viewModel.confirmEndGoalLock() },
        )
    }
}

@Composable
private fun GoalLockDetailContent(
    modifier: Modifier = Modifier,
    state: GoalLockDetailUiState,
    onRequestEnd: () -> Unit,
    onCancelEnd: () -> Unit,
    onConfirmEnd: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.goalLock == null) {
            Text(
                text = "목표 잠금을 불러오는 중입니다.",
                color = KeepTheme.colors.onSurfaceVariant,
            )
            return@Column
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.goalName,
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text(
                    text = "${state.lockModeLabel} · ${state.selectedAppCount}개 앱",
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Text(
                    text = when {
                        state.isCompleted -> "완료된 목표 잠금입니다."
                        state.isEnded -> "종료된 목표 잠금입니다."
                        else -> "진행 중인 목표 잠금입니다."
                    },
                    color = KeepTheme.colors.surfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }

        if (!state.isEnded && !state.isCompleted) {
            if (state.showEndConfirmation) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "목표 잠금을 끝내면 오늘부터 선택한 앱이 다시 열릴 수 있어요. 지금 종료할까요?",
                            color = KeepTheme.colors.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(onClick = onCancelEnd) {
                                Text(text = "계속 유지")
                            }
                            Button(onClick = onConfirmEnd) {
                                Text(text = "종료")
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = onRequestEnd) {
                    Text(text = "목표 잠금 종료")
                }
            }
        }
    }
}
