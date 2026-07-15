package com.uiery.keep.feature.onboarding.usageanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun UsageAnalysisScreen(
    onNavigateProposal: (Long) -> Unit,
    onNavigateManualAppSelect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsageAnalysisViewModel = hiltViewModel(),
) {
    viewModel.collectSideEffect { effect ->
        when (effect) {
            is UsageAnalysisSideEffect.NavigateProposal -> onNavigateProposal(effect.averageDailyMinutes)
            UsageAnalysisSideEffect.NavigateManualAppSelect -> onNavigateManualAppSelect()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.onStepViewed()
        viewModel.startAnalysis()
    }
    Scaffold(modifier = modifier.fillMaxSize(), containerColor = KeepTheme.colors.background) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = KeepTheme.colors.primary)
            Text(
                modifier = Modifier.padding(top = 24.dp),
                text = stringResource(R.string.first_promise_analysis_title),
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 30.sp,
            )
        }
    }
}
