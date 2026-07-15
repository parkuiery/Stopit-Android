package com.uiery.keep.feature.onboarding.usageanalysis

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.uiery.keep.feature.onboarding.Onboarding

data class TransientAnalysisProposal(
    val draftId: String,
    val averageDailyMinutes: Long,
)

internal object FirstPromiseAnalysisTransientHolder {
    private var proposal: TransientAnalysisProposal? = null

    @Synchronized
    fun store(value: TransientAnalysisProposal) {
        proposal = value
    }

    @Synchronized
    fun peek(draftId: String): Long? =
        proposal?.takeIf { it.draftId == draftId }?.averageDailyMinutes

    @Synchronized
    fun consume(draftId: String): Long? {
        val value = proposal ?: return null
        proposal = null
        return value.takeIf { it.draftId == draftId }?.averageDailyMinutes
    }

    @Synchronized
    fun clear() {
        proposal = null
    }
}

internal fun storeTransientAverageThenNavigate(
    proposal: TransientAnalysisProposal,
    navigate: () -> Unit,
) {
    FirstPromiseAnalysisTransientHolder.store(proposal)
    navigate()
}

fun NavController.navigateToUsageAnalysis(navOptions: NavOptions? = null) = navigate(Onboarding.Route.UsageAnalysis, navOptions)

fun NavController.navigateToPromiseProposal(proposal: TransientAnalysisProposal) =
    storeTransientAverageThenNavigate(proposal) { navigate(Onboarding.Route.PromiseProposal) }

fun NavGraphBuilder.usageAnalysisScreen(
    onNavigateProposal: (TransientAnalysisProposal) -> Unit,
    onNavigateManualAppSelect: () -> Unit,
) {
    composable<Onboarding.Route.UsageAnalysis> {
        UsageAnalysisScreen(onNavigateProposal, onNavigateManualAppSelect)
    }
}
