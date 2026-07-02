package com.uiery.kds

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uiery.kds.theme.KeepTheme

/** Visual treatments for [KeepButton]. */
enum class KeepButtonVariant {
    /** Primary filled action in brand orange. */
    Primary,

    /** Destructive action rendered as an outlined red button. */
    Destructive,
}

@Composable
fun KeepButton(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    variant: KeepButtonVariant = KeepButtonVariant.Primary,
    bottomSpacing: Boolean = true,
    onClick: () -> Unit,
) {
    val outerModifier = if (bottomSpacing) modifier.padding(bottom = 24.dp) else modifier
    val shape = RoundedCornerShape(12.dp)
    val contentPadding = PaddingValues(vertical = 18.dp, horizontal = 24.dp)

    when (variant) {
        KeepButtonVariant.Primary -> Button(
            modifier = outerModifier,
            onClick = onClick,
            shape = shape,
            enabled = enabled,
            colors = ButtonColors(
                containerColor = KeepTheme.colors.primary,
                contentColor = Color.White,
                disabledContainerColor = KeepTheme.colors.tertiaryContainer,
                disabledContentColor = Color.White,
            ),
            contentPadding = contentPadding,
        ) {
            KeepButtonLabel(text = text)
        }

        KeepButtonVariant.Destructive -> OutlinedButton(
            modifier = outerModifier,
            onClick = onClick,
            shape = shape,
            enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = KeepTheme.colors.error,
                disabledContentColor = KeepTheme.colors.error.copy(alpha = 0.4f),
            ),
            border = BorderStroke(
                width = 1.dp,
                color = KeepTheme.colors.error.copy(alpha = if (enabled) 0.5f else 0.2f),
            ),
            contentPadding = contentPadding,
        ) {
            KeepButtonLabel(text = text)
        }
    }
}

@Composable
private fun KeepButtonLabel(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    )
}
