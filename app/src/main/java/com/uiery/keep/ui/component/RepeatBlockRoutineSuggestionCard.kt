package com.uiery.keep.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestion

/**
 * Shared card for repeat-block routine suggestions shown on Block, Home, and LockHistory surfaces.
 *
 * Screen owners provide surface-specific copy and optional action test tags while this component owns
 * the common hierarchy, spacing, CTA order, and dismiss affordance.
 */
@Composable
fun RepeatBlockRoutineSuggestionCard(
    modifier: Modifier = Modifier,
    suggestion: RepeatBlockRoutineSuggestion,
    @StringRes titleResId: Int,
    @StringRes messageResId: Int,
    onApplyClick: () -> Unit,
    onDismissClick: () -> Unit,
    applyActionTestTag: String? = null,
    dismissActionTestTag: String? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(titleResId),
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = androidx.compose.ui.res.stringResource(
                    messageResId,
                    suggestion.prefillPackages.size,
                    suggestion.prefillStartTime,
                    suggestion.prefillEndTime,
                ),
                color = KeepTheme.colors.surfaceVariant,
                fontSize = 13.sp,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KeepButton(
                    modifier = applyActionTestTag?.let { Modifier.testTag(it) } ?: Modifier,
                    text = androidx.compose.ui.res.stringResource(R.string.repeat_block_suggestion_apply_button),
                    onClick = onApplyClick,
                )
                TextButton(
                    modifier = dismissActionTestTag?.let { Modifier.testTag(it) } ?: Modifier,
                    onClick = onDismissClick,
                ) {
                    Text(text = androidx.compose.ui.res.stringResource(R.string.repeat_block_suggestion_dismiss_button))
                }
            }
        }
    }
}
