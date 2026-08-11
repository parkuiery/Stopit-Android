package com.uiery.keep.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.uiery.kds.KeepConfirmationDialog
import com.uiery.kds.KeepConfirmationTone
import com.uiery.keep.R
import com.uiery.keep.domain.websiteblocking.WebsiteLockRecommendation
import com.uiery.keep.domain.websiteblocking.WebsiteLockRecommendationCopy

/**
 * 앱을 고르면 같은 서비스의 웹사이트도 함께 막을지 물어본다. 앱만 막은 잠금은 브라우저로
 * 그대로 우회되므로 제안할 가치가 있지만, 도메인은 사용자가 명시적으로 수락할 때만 추가한다.
 */
@Composable
fun WebsiteLockRecommendationDialog(
    recommendations: List<WebsiteLockRecommendation>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (recommendations.isEmpty()) return

    val message = stringResource(
        R.string.website_lock_recommendation_message,
        WebsiteLockRecommendationCopy.serviceNames(recommendations),
    )
    val domains = stringResource(
        R.string.website_lock_recommendation_domains,
        WebsiteLockRecommendationCopy.domains(recommendations),
    )

    KeepConfirmationDialog(
        title = stringResource(R.string.website_lock_recommendation_title),
        message = "$message\n\n$domains",
        confirmLabel = stringResource(R.string.website_lock_recommendation_accept),
        dismissLabel = stringResource(R.string.website_lock_recommendation_skip),
        confirmTone = KeepConfirmationTone.Brand,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
