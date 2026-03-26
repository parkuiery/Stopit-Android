package com.uiery.keep.feature.lock.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import kotlinx.coroutines.delay

private enum class UnlockStep { REASON, APPS, DURATION, COUNTDOWN }

private data class UnlockReason(val stringRes: Int, val key: String)

private val REASONS = listOf(
    UnlockReason(R.string.emergency_unlock_reason_work, "work"),
    UnlockReason(R.string.emergency_unlock_reason_contact, "contact"),
    UnlockReason(R.string.emergency_unlock_reason_info, "info"),
    UnlockReason(R.string.emergency_unlock_reason_habit, "habit"),
    UnlockReason(R.string.emergency_unlock_reason_boredom, "boredom"),
    UnlockReason(R.string.emergency_unlock_reason_other, "other"),
)

private val DURATION_OPTIONS = listOf(3, 5, 10)

private val STEPS = UnlockStep.entries.toList()

@Composable
fun EmergencyUnlockBottomSheetContent(
    blockedApps: Set<String>,
    onUnlock: (reason: String, customReason: String?, apps: Set<String>, durationMinutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(UnlockStep.REASON) }
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var customReason by remember { mutableStateOf("") }
    var selectedApps by remember { mutableStateOf(emptySet<String>()) }
    var selectedDuration by remember { mutableIntStateOf(5) }
    var countdownSeconds by remember { mutableIntStateOf(30) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        // Step indicator
        if (step != UnlockStep.COUNTDOWN) {
            StepIndicator(
                currentStep = STEPS.indexOf(step),
                totalSteps = 3,
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        AnimatedContent(
            targetState = step,
            label = "unlock_step",
            transitionSpec = {
                val forward = STEPS.indexOf(targetState) > STEPS.indexOf(initialState)
                if (forward) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
        ) { currentStep ->
            when (currentStep) {
                UnlockStep.REASON -> ReasonStep(
                    selectedReason = selectedReason,
                    customReason = customReason,
                    onReasonSelected = { selectedReason = it },
                    onCustomReasonChanged = { customReason = it },
                    onNext = { step = UnlockStep.APPS },
                )
                UnlockStep.APPS -> AppSelectionStep(
                    blockedApps = blockedApps,
                    selectedApps = selectedApps,
                    onSelectionChanged = { selectedApps = it },
                    onNext = { step = UnlockStep.DURATION },
                )
                UnlockStep.DURATION -> DurationStep(
                    selectedDuration = selectedDuration,
                    onDurationSelected = { selectedDuration = it },
                    onRequest = { step = UnlockStep.COUNTDOWN },
                )
                UnlockStep.COUNTDOWN -> CountdownStep(
                    seconds = countdownSeconds,
                    onTick = { countdownSeconds = it },
                    onComplete = {
                        onUnlock(
                            selectedReason ?: "",
                            if (selectedReason == "other") customReason else null,
                            selectedApps,
                            selectedDuration,
                        )
                    },
                    onCancel = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val isActive = index <= currentStep
            val width by animateFloatAsState(
                targetValue = if (index == currentStep) 24f else 8f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "step_width",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) KeepTheme.colors.primary
                        else KeepTheme.colors.primary.copy(alpha = 0.2f)
                    ),
            )
        }
    }
}

@Composable
private fun ReasonStep(
    selectedReason: String?,
    customReason: String,
    onReasonSelected: (String) -> Unit,
    onCustomReasonChanged: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        Text(
            text = stringResource(R.string.emergency_unlock_reason_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = KeepTheme.colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        REASONS.forEach { reason ->
            val isSelected = selectedReason == reason.key
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) KeepTheme.colors.primary.copy(alpha = 0.08f)
                        else KeepTheme.colors.onSecondary.copy(alpha = 0.5f)
                    )
                    .selectable(
                        selected = isSelected,
                        onClick = { onReasonSelected(reason.key) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(reason.stringRes),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontSize = 15.sp,
                )
            }
        }
        if (selectedReason == "other") {
            OutlinedTextField(
                value = customReason,
                onValueChange = onCustomReasonChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = {
                    Text(text = stringResource(R.string.emergency_unlock_reason_other_hint))
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        KeepButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.emergency_unlock_next),
            enabled = selectedReason != null && (selectedReason != "other" || customReason.isNotBlank()),
            onClick = onNext,
        )
    }
}

@Composable
private fun AppSelectionStep(
    blockedApps: Set<String>,
    selectedApps: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val density = context.resources.displayMetrics.density
    val iconSizePx = (40 * density).toInt()

    Column {
        Text(
            text = stringResource(R.string.emergency_unlock_select_apps),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = KeepTheme.colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(blockedApps.toList()) { packageName ->
                val appInfo = remember(packageName) {
                    runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
                }
                val appName = remember(appInfo) {
                    appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName
                }
                val appIcon = remember(appInfo) {
                    appInfo?.let { pm.getApplicationIcon(it) }
                }
                val isSelected = selectedApps.contains(packageName)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) KeepTheme.colors.primary.copy(alpha = 0.08f)
                            else KeepTheme.colors.onSecondary.copy(alpha = 0.5f)
                        )
                        .clickable {
                            onSelectionChanged(
                                if (isSelected) selectedApps - packageName
                                else selectedApps + packageName
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    appIcon?.let {
                        Image(
                            bitmap = it.toBitmap(iconSizePx, iconSizePx).asImageBitmap(),
                            contentDescription = appName,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = appName,
                        color = KeepTheme.colors.onSurfaceVariant,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        KeepButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.emergency_unlock_next),
            enabled = selectedApps.isNotEmpty(),
            onClick = onNext,
        )
    }
}

@Composable
private fun DurationStep(
    selectedDuration: Int,
    onDurationSelected: (Int) -> Unit,
    onRequest: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.emergency_unlock_select_duration),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = KeepTheme.colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DURATION_OPTIONS.forEach { minutes ->
                FilterChip(
                    selected = selectedDuration == minutes,
                    onClick = { onDurationSelected(minutes) },
                    label = {
                        Text(
                            text = stringResource(R.string.emergency_unlock_duration_minutes, minutes),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontWeight = if (selectedDuration == minutes) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KeepTheme.colors.primary.copy(alpha = 0.12f),
                        selectedLabelColor = KeepTheme.colors.primary,
                    ),
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        KeepButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.emergency_unlock_request),
            onClick = onRequest,
        )
    }
}

@Composable
private fun CountdownStep(
    seconds: Int,
    onTick: (Int) -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    LaunchedEffect(Unit) {
        var remaining = seconds
        while (remaining > 0) {
            delay(1000)
            remaining--
            onTick(remaining)
        }
        onComplete()
    }

    val progress by animateFloatAsState(
        targetValue = seconds / 30f,
        animationSpec = tween(durationMillis = 900),
        label = "countdown_progress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.emergency_unlock_waiting),
            fontSize = 16.sp,
            color = KeepTheme.colors.surfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(140.dp),
                strokeWidth = 10.dp,
                strokeCap = StrokeCap.Round,
                color = KeepTheme.colors.primary,
                trackColor = KeepTheme.colors.primary.copy(alpha = 0.1f),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$seconds",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = KeepTheme.colors.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.emergency_unlock_waiting_seconds_label),
                    fontSize = 13.sp,
                    color = KeepTheme.colors.surfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        TextButton(
            onClick = onCancel,
        ) {
            Text(
                text = stringResource(R.string.emergency_unlock_cancel),
                color = KeepTheme.colors.surfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
        }
    }
}
