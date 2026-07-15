package com.uiery.keep.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.annotation.DrawableRes
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.KeepSnackBar
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.analytics.AdPlacement
import com.uiery.keep.analytics.AdPlacementMetadata
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.TrackedBannerAd
import com.uiery.keep.feature.home.component.ContentDescription
import com.uiery.keep.feature.home.component.TimeBottomSheetContent
import com.uiery.keep.domain.repeatblock.RepeatBlockRoutineSuggestion
import com.uiery.keep.domain.usageinsight.UsageInsightRoutinePrefill
import com.uiery.keep.feature.home.component.UsageInsightCard
import com.uiery.keep.feature.home.component.FirstPromiseResumeCard
import com.uiery.keep.feature.routine.RoutineAlarmPermissionSettingsLauncher
import com.uiery.keep.feature.routine.createAppDetailsSettingsIntent
import com.uiery.keep.feature.routine.createExactAlarmSettingsIntent
import com.uiery.keep.ui.component.CategoryBottomSheetContent
import com.uiery.keep.ui.component.CategoryButton
import com.uiery.keep.ui.component.PermissionSettingDialog
import com.uiery.kds.KeepSwitch
import com.uiery.keep.util.hasAccessibilityPermission
import com.uiery.keep.util.requestAccessibilityPermission
import com.uiery.keep.util.toPx
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateMenu: () -> Unit,
    onNavigateLock: (lockTime: String?, Boolean) -> Unit,
    onNavigateLockHistory: () -> Unit = {},
    onNavigateRoutine: (routineSavedEntrySurface: String?, routineSavedCreationSource: String?) -> Unit = { _, _ -> },
    onNavigateGoalLockDetail: (goalLockId: Long) -> Unit = {},
    onNavigateRoutineWithRepeatBlockPrefill: (RepeatBlockRoutineSuggestion) -> Unit = {},
    onNavigateRoutineWithUsageInsightPrefill: (UsageInsightRoutinePrefill) -> Unit = {},
) {
    val uiState by viewModel.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val categoryBottomSheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )
    val timeBottomSheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )
    val context = LocalContext.current
    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.RawRes(R.raw.home_prevent),
    )
    val haptic = LocalHapticFeedback.current
    var openAlertDialog by remember { mutableStateOf(false) }
    val syncAccessibilityPermissionDialogState = {
        openAlertDialog = !hasAccessibilityPermission(context)
    }
    val noSelectedAppsMessage = stringResource(R.string.select_apps_to_lock)

    viewModel.collectSideEffect { effect ->
        when (effect) {
            is HomeSideEffect.ShowSnackBar -> {
                coroutineScope.launch {
                    val job =
                        launch {
                            snackBarHostState.showSnackbar(
                                message = effect.message,
                            )
                        }
                    delay(2000L)
                    job.cancel()
                }
            }

            is HomeSideEffect.MoveToLock -> onNavigateLock(effect.lockTime, effect.isRoutine)
            is HomeSideEffect.MoveToRoutine -> onNavigateRoutine(
                effect.routineSavedEntrySurface,
                effect.routineSavedCreationSource,
            )
            is HomeSideEffect.NavigateToRoutineWithRepeatBlockPrefill ->
                onNavigateRoutineWithRepeatBlockPrefill(effect.suggestion)

            is HomeSideEffect.NavigateToRoutineWithUsageInsightPrefill ->
                onNavigateRoutineWithUsageInsightPrefill(effect.prefill)

            is HomeSideEffect.OpenUsageAccessSettings ->
                // 일부 OEM/Go 빌드는 이 설정 화면을 resolve 하지 못해 ActivityNotFoundException 을 던진다.
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            is HomeSideEffect.OpenExactAlarmSettings ->
                RoutineAlarmPermissionSettingsLauncher.open(
                    exactAlarmTarget = createExactAlarmSettingsIntent(context.packageName),
                    appDetailsTarget = createAppDetailsSettingsIntent(context.packageName),
                    launch = context::startActivity,
                )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.analyticsHomeScreen()
        syncAccessibilityPermissionDialogState()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    val observedLifecycle = (activity as? LifecycleOwner)?.lifecycle ?: lifecycleOwner.lifecycle
    DisposableEffect(observedLifecycle, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncAccessibilityPermissionDialogState()
                viewModel.maybeDrainRoutineStartNotice()
                viewModel.maybeDrainReviewFlag(activity)
                // Usage Access 설정 딥링크에서 복귀했을 때 인사이트 카드를 재평가한다(권한 전환 감지).
                viewModel.loadUsageInsightCard()
                viewModel.onFirstPromiseExactAlarmResume()
            }
        }
        observedLifecycle.addObserver(observer)
        onDispose { observedLifecycle.removeObserver(observer) }
    }

    if (openAlertDialog) {
        PermissionSettingDialog(
            onDismissRequest = { openAlertDialog = false },
            onConfirmation = {
                openAlertDialog = false
                requestAccessibilityPermission(context)
            },
        )
    }

    if (uiState.isShowCategoryBottomSheet) {
        KeepModalBottomSheet(
            sheetState = categoryBottomSheetState,
            onDismissRequest = viewModel::hideCategoryBottomSheet,
        ) {
            CategoryBottomSheetContent(
                storeSelectApps = uiState.selectedAppPackage,
                onComplete = { selectPackages ->
                    viewModel.selectCategoryComplete(selectPackages)
                    coroutineScope
                        .launch {
                            categoryBottomSheetState.hide()
                        }.invokeOnCompletion {
                            if (!categoryBottomSheetState.isVisible) {
                                viewModel.hideCategoryBottomSheet()
                            }
                        }
                },
            )
        }
    }

    if (uiState.isShowTimeBottomSheet) {
        KeepModalBottomSheet(
            sheetState = timeBottomSheetState,
            onDismissRequest = viewModel::hideTimeBottomSheet,
        ) {
            TimeBottomSheetContent(
                blockTime = uiState.blockTime,
                countdownDays = uiState.countdownDays,
                countdownTime = uiState.countdownTime,
                onChangeCountdownDuration = viewModel::updateCountdownDuration,
                onChangeTimerTIme = viewModel::updateTimerTime,
                onLockClick = {
                    if (uiState.selectedAppPackage.isEmpty()) {
                        viewModel.lockTime(noSelectedAppsMessage = noSelectedAppsMessage)
                    } else {
                        viewModel.lockTime()
                        coroutineScope
                            .launch {
                                timeBottomSheetState.hide()
                            }.invokeOnCompletion {
                                if (!timeBottomSheetState.isVisible) {
                                    viewModel.hideTimeBottomSheet()
                                    viewModel.moveToLock()
                                }
                            }
                    }
                },
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = onNavigateMenu) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_format_list_bulleted_24),
                            contentDescription = stringResource(R.string.cd_open_menu),
                            tint = KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = KeepTheme.colors.background,
                    ),
            )
        },
        snackbarHost = {
            Box(modifier = Modifier.fillMaxSize()) {
                SnackbarHost(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding(),
                    hostState = snackBarHostState,
                    snackbar = {
                        KeepSnackBar(snackbarData = it)
                    },
                )
            }
        },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues),
        ) {
            val configuration = LocalConfiguration.current
            var lottieOffset by remember { mutableStateOf(IntOffset.Zero) }
            var parentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val calculateLottieOffset: (LayoutCoordinates) -> Unit = { coordinates ->
                val boundsInParent = coordinates.boundsInParent().size
                parentCoordinates?.let {
                    val positionInRoot = it.localPositionOf(coordinates, Offset.Zero)
                    val lottieWidthCenter = (configuration.screenWidthDp.dp / 2).toPx(context)
                    val lottieHeightCenter = (positionInRoot.y.dp / 2).toPx(context)
                    lottieOffset =
                        IntOffset(
                            x = (positionInRoot.x + boundsInParent.width / 2 - lottieWidthCenter).toInt(),
                            y = (positionInRoot.y + boundsInParent.height / 2 - lottieHeightCenter).toInt(),
                        )
                }
            }
            CategoryButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                onClick = viewModel::showCategoryBottomSheet,
                enabled = !uiState.isKeep,
                categorySize = uiState.selectedAppPackage.size,
            )
            if (uiState.showFirstLockActivationCta) {
                FirstLockActivationCta(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    onClick = { viewModel.changeIsKeep() },
                )
            }
            FirstPromiseResumeCard(
                state = uiState.firstPromiseResumeCard,
                onActivate = viewModel::activateFirstPromiseResumeCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
            UsageInsightCard(
                state = uiState.usageInsightCard,
                onCtaClick = viewModel::onUsageInsightCtaClick,
                onDismiss = viewModel::onUsageInsightDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { parentCoordinates = it },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize(),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                space = 20.dp,
                                alignment = Alignment.CenterVertically,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val (image, message) =
                            if (uiState.isKeep) {
                                R.drawable.kepp_icon to stringResource(R.string.keep_turned_off)
                            } else {
                                R.drawable.disable_logo to
                                    stringResource(R.string.keep_turned_on)
                            }
                        Image(
                            modifier =
                                Modifier
                                    .sizeIn(
                                        minHeight = 100.dp,
                                        minWidth = 100.dp,
                                    ).onGloballyPositioned(calculateLottieOffset)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!uiState.isKeep && uiState.selectedAppPackage.isEmpty()) {
                                            viewModel.changeIsKeep(noSelectedAppsMessage = noSelectedAppsMessage)
                                        } else {
                                            viewModel.showSnackBar(message)
                                            viewModel.changeIsKeep()
                                        }
                                    },
                            painter = painterResource(id = image),
                            contentDescription = null,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            KeepSwitch(
                                checked = uiState.isKeep,
                                onCheckedChange = {
                                    if (!uiState.isKeep && uiState.selectedAppPackage.isEmpty()) {
                                        viewModel.changeIsKeep(noSelectedAppsMessage = noSelectedAppsMessage)
                                    } else {
                                        viewModel.showSnackBar(message)
                                        viewModel.changeIsKeep()
                                    }
                                },
                            )
                            Image(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .background(
                                            shape = RoundedCornerShape(8.dp),
                                            color = KeepTheme.colors.onSecondary,
                                        ).clip(RoundedCornerShape(8.dp))
                                        .clickable(
                                            onClick = viewModel::showTimeBottomSheet,
                                            enabled = !uiState.isKeep,
                                        ).padding(4.dp),
                                painter = painterResource(id = R.drawable.timer_outline),
                                contentDescription = null,
                            )
                        }
                    }
                    if (uiState.isKeep) {
                        LottieAnimation(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .absoluteOffset { lottieOffset },
                            composition = composition,
                            iterations = LottieConstants.IterateForever,
                        )
                    }
                }
                Column {
                    ContentDescription(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                        isKeep = uiState.isKeep,
                        startTime = uiState.startTime,
                    )
                    TrackedBannerAd(
                        metadata = AdPlacementMetadata(
                            screenName = KeepAnalyticsScreen.HOME,
                            screenContext = "main",
                            placement = AdPlacement.HomeBottom.analyticsPlacement,
                            adUnitId = AdPlacement.HomeBottom.adUnitId,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FirstLockActivationCta(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(KeepTheme.colors.onSecondary)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.first_lock_activation_cta_title),
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = stringResource(R.string.first_lock_activation_cta_description),
                color = KeepTheme.colors.surfaceVariant,
                fontSize = 13.sp,
            )
        }
        Text(
            text = stringResource(R.string.first_lock_activation_cta_action),
            color = KeepTheme.colors.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@Composable
internal fun GoalLockProgressCard(
    modifier: Modifier = Modifier,
    cardState: HomeGoalLockCardState,
    onClick: () -> Unit,
) {
    val displayCopy = cardState.displayCopy()
    val title = stringResource(displayCopy.titleResId)
    val lockMode = stringResource(displayCopy.lockModeResId)
    val summary = stringResource(
        displayCopy.summaryResId,
        cardState.daysRemaining,
        lockMode,
        cardState.selectedAppCount,
    )
    val talkBackSummary = listOf(
        title,
        cardState.goalName,
        summary,
    ).joinToString(", ")
    Card(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = talkBackSummary
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = cardState.goalName,
                color = KeepTheme.colors.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Text(
                text = summary,
                color = KeepTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun HomeQuickAction(
    modifier: Modifier = Modifier,
    @DrawableRes iconResId: Int,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = KeepTheme.colors.onSurfaceVariant,
        )
        Text(
            text = label,
            color = KeepTheme.colors.surfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
internal fun HomeStatusCtaCard(
    modifier: Modifier = Modifier,
    model: HomeStatusCtaModel,
    onPrimaryClick: () -> Unit,
    onChangeAppsClick: () -> Unit,
    onTimerClick: () -> Unit,
    onLockHistoryClick: () -> Unit,
    onRoutineCreationClick: () -> Unit,
) {
    Card(
        modifier = modifier.testTag("home_status_cta_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = KeepTheme.colors.onSecondary),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val title = when (model.statusKind) {
                HomeStatusKind.NO_SELECTED_APPS -> stringResource(model.titleResId)
                else -> stringResource(model.titleResId, model.selectedAppCount)
            }
            Text(
                text = title,
                color = KeepTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                text = stringResource(model.descriptionResId),
                color = KeepTheme.colors.surfaceVariant,
                fontSize = 13.sp,
            )
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(model.primaryCtaResId),
                enabled = model.shouldOpenAppSelection || model.shouldToggleKeep,
                onClick = onPrimaryClick,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (model.showChangeAppsSecondary) {
                    HomeQuickAction(
                        modifier = Modifier.weight(1f),
                        iconResId = R.drawable.baseline_format_list_bulleted_24,
                        label = stringResource(R.string.home_secondary_change_apps),
                        onClick = onChangeAppsClick,
                    )
                }
                if (model.timerEnabled) {
                    HomeQuickAction(
                        modifier = Modifier.weight(1f),
                        iconResId = R.drawable.timer_outline,
                        label = stringResource(R.string.home_secondary_timer),
                        onClick = onTimerClick,
                    )
                }
                if (model.showLockHistorySecondary) {
                    HomeQuickAction(
                        modifier = Modifier.weight(1f),
                        iconResId = R.drawable.ic_history,
                        label = stringResource(R.string.home_secondary_lock_history),
                        onClick = onLockHistoryClick,
                    )
                }
                if (model.showRoutineCreationSecondary) {
                    HomeQuickAction(
                        modifier = Modifier.weight(1f),
                        iconResId = R.drawable.ic_routine,
                        label = stringResource(R.string.home_secondary_create_routine),
                        onClick = onRoutineCreationClick,
                    )
                }
            }
        }
    }
}
