package com.uiery.keep.feature.onboarding.usageanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.uiery.kds.KeepCircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun UsageAnalysisScreen(
    onNavigateProposal: (TransientAnalysisProposal) -> Unit,
    onNavigateManualAppSelect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UsageAnalysisViewModel = hiltViewModel(),
) {
    val analysisStatus = stringResource(R.string.first_promise_analysis_status)
    viewModel.collectSideEffect { effect ->
        when (effect) {
            is UsageAnalysisSideEffect.NavigateProposal -> onNavigateProposal(effect.proposal)
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
            Text(
                modifier = Modifier.semantics { heading() },
                text = stringResource(R.string.first_promise_analysis_title),
                color = KeepTheme.colors.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(24.dp))
            KeepCircularProgressIndicator(
                modifier = Modifier.semantics { contentDescription = analysisStatus },
                color = KeepTheme.colors.primary,
            )
        }
    }
}
