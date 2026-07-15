package com.uiery.keep.feature.onboarding.goal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun GoalSelectScreen(
    onNavigateUsageAccess: () -> Unit,
    onNavigateManualAppSelect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GoalSelectViewModel = hiltViewModel(),
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { effect ->
        when (effect) {
            GoalSelectSideEffect.NavigateUsageAccess -> onNavigateUsageAccess()
            GoalSelectSideEffect.NavigateManualAppSelect -> onNavigateManualAppSelect()
        }
    }
    LaunchedEffect(viewModel) { viewModel.onStepViewed() }

    Scaffold(modifier = modifier.fillMaxSize(), containerColor = KeepTheme.colors.background) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    modifier = Modifier.padding(top = 36.dp),
                    text = stringResource(R.string.first_promise_goal_title),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                )
                Spacer(Modifier.height(24.dp))
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GoalOption(FirstPromiseGoal.Sleep, R.string.first_promise_goal_sleep, state.selectedGoal, viewModel::selectGoal)
                    GoalOption(FirstPromiseGoal.Focus, R.string.first_promise_goal_focus, state.selectedGoal, viewModel::selectGoal)
                    GoalOption(FirstPromiseGoal.Study, R.string.first_promise_goal_study, state.selectedGoal, viewModel::selectGoal)
                    GoalOption(FirstPromiseGoal.FreeTime, R.string.first_promise_goal_free_time, state.selectedGoal, viewModel::selectGoal)
                }
                Spacer(Modifier.height(24.dp))
            }
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.first_promise_check_pattern),
                enabled = state.canContinue,
                bottomSpacing = false,
                onClick = viewModel::continuePersonalized,
            )
            TextButton(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                onClick = viewModel::chooseManual,
            ) {
                Text(stringResource(R.string.first_promise_manual_setup), color = KeepTheme.colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GoalOption(
    goal: FirstPromiseGoal,
    label: Int,
    selectedGoal: FirstPromiseGoal?,
    onSelect: (FirstPromiseGoal) -> Unit,
) {
    val selected = selectedGoal == goal
    Card(
        modifier = Modifier.fillMaxWidth().selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = { onSelect(goal) },
        ),
        colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) KeepTheme.colors.primary else KeepTheme.colors.tertiary),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(
                modifier = Modifier.padding(start = 12.dp),
                text = stringResource(label),
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
        }
    }
}
