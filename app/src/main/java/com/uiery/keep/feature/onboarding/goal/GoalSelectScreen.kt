package com.uiery.keep.feature.onboarding.goal

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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.uiery.kds.KeepCard
import com.uiery.kds.KeepCardVariant
import androidx.compose.material3.MaterialTheme
import com.uiery.kds.KeepRadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.firstpromise.FirstPromiseGoal
import com.uiery.keep.feature.onboarding.OnboardingActionStack
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect
import com.uiery.keep.ui.component.withoutBottomInset

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
            modifier = Modifier.fillMaxSize().padding(insets.withoutBottomInset()),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    modifier = Modifier.padding(top = 36.dp).semantics { heading() },
                    text = stringResource(R.string.first_promise_goal_title),
                    color = KeepTheme.colors.onSurfaceVariant,
                    style = MaterialTheme.typography.titleLarge,
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
            OnboardingActionStack(
                primaryText = stringResource(R.string.first_promise_check_pattern),
                secondaryText = stringResource(R.string.first_promise_manual_setup),
                primaryEnabled = state.canContinue,
                onPrimaryClick = viewModel::continuePersonalized,
                onSecondaryClick = viewModel::chooseManual,
            )
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
    val shape = RoundedCornerShape(12.dp)
    KeepCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(goal) },
            ),
        shape = shape,
        variant = if (selected) KeepCardVariant.BrandWeak else KeepCardVariant.LayerDefault,
        bordered = true,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeepRadioButton(
                selected = selected,
                onClick = null,
            )
            Text(
                modifier = Modifier.padding(start = 12.dp),
                text = stringResource(label),
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
