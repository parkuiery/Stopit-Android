package com.uiery.keep.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import com.uiery.kds.KeepCard
import androidx.compose.material3.Icon
import com.uiery.kds.KeepIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.uiery.kds.KeepTopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepSnackbarHost
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.BuildConfig
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
import com.uiery.keep.feature.routine.RoutineAlarmPermissionSettingsLaunchResult
import com.uiery.keep.feature.routine.createAppDetailsSettingsIntent
import com.uiery.keep.feature.routine.createExactAlarmSettingsIntent
import com.uiery.keep.ui.component.CategoryBottomSheetContent
import com.uiery.keep.ui.component.CategoryButton
import com.uiery.keep.ui.component.PermissionSettingDialog
import com.uiery.keep.ui.component.WebsiteBlockingUnavailableBanner
import com.uiery.keep.ui.component.currentWebsiteBlockingWarning
import com.uiery.keep.ui.component.WebsiteLockRecommendationDialog
import com.uiery.kds.KeepSwitch
import com.uiery.keep.websiteblocking.WebsiteBlockingVpnController
import com.uiery.keep.util.hasAccessibilityPermission
import com.uiery.keep.util.requestAccessibilityPermission
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
                if (RoutineAlarmPermissionSettingsLauncher.open(
                    exactAlarmTarget = createExactAlarmSettingsIntent(context.packageName),
                    appDetailsTarget = createAppDetailsSettingsIntent(context.packageName),
                    launch = context::startActivity,
                ) == RoutineAlarmPermissionSettingsLaunchResult.Unavailable) {
                    viewModel.onFirstPromiseExactAlarmSettingsUnavailable()
                }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.analyticsHomeScreen()
        syncAccessibilityPermissionDialogState()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    val observedLifecycle = (activity as? LifecycleOwner)?.lifecycle ?: lifecycleOwner.lifecycle
    // 돌아온 횟수. 웹 차단 판정은 값이 바뀔 때만 도는데, 창 안에서 서비스만 죽으면 판정은
    // 그대로라 아무도 다시 세우지 않는다. 돌아온 사실 자체가 재확인 계기가 되어야 한다.
    var resumeCount by rememberSaveable { mutableIntStateOf(0) }
    DisposableEffect(observedLifecycle, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncAccessibilityPermissionDialogState()
                viewModel.maybeDrainRoutineStartNotice()
                viewModel.maybeDrainReviewFlag(activity)
                // Usage Access 설정 딥링크에서 복귀했을 때 인사이트 카드를 재평가한다(권한 전환 감지).
                viewModel.loadUsageInsightCard()
                viewModel.onFirstPromiseExactAlarmResume()
                // 알람이 지연·누락되었거나 창 도중 재부팅한 회차는 여기서 되살아난다.
                viewModel.refreshRoutineWebsiteSession()
                resumeCount += 1
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

    // 잠금이 켜지고 꺼지는 것을 따라 웹 차단 VPN 도 서고 내린다. 시스템 동의는 액티비티
    // 결과로만 받을 수 있어 이 판단이 화면에 붙어 있다.
    val websiteBlockingConsentDeniedMessage = stringResource(R.string.website_blocking_consent_denied)
    WebsiteBlockingVpnController(
        decision = uiState.websiteBlockingRuntimeDecision(),
        resumeCount = resumeCount,
        onConsentDenied = { viewModel.showSnackBar(websiteBlockingConsentDeniedMessage) },
    )

    // 웹사이트 탭이 닫혀 있는 빌드에서는 추천도 꺼낼 자리가 없다.
    if (BuildConfig.WEBSITE_BLOCKING_ENABLED) {
        WebsiteLockRecommendationDialog(
            recommendations = uiState.pendingWebsiteRecommendations,
            onConfirm = viewModel::acceptWebsiteLockRecommendations,
            onDismiss = viewModel::dismissWebsiteLockRecommendations,
        )
    }

    if (uiState.isShowCategoryBottomSheet) {
        val closeCategoryBottomSheet = {
            coroutineScope
                .launch {
                    categoryBottomSheetState.hide()
                }.invokeOnCompletion {
                    if (!categoryBottomSheetState.isVisible) {
                        viewModel.hideCategoryBottomSheet()
                    }
                }
            Unit
        }
        KeepModalBottomSheet(
            sheetState = categoryBottomSheetState,
            onDismissRequest = viewModel::hideCategoryBottomSheet,
        ) {
            CategoryBottomSheetContent(
                storeSelectApps = uiState.selectedAppPackage,
                websiteSelectionEnabled = BuildConfig.WEBSITE_BLOCKING_ENABLED,
                storeSelectedWebDomains = uiState.selectedWebDomains,
                onCompleteTargets = { selectPackages, selectWebDomains ->
                    viewModel.selectLockTargetsComplete(selectPackages, selectWebDomains)
                    closeCategoryBottomSheet()
                },
                onComplete = { selectPackages ->
                    viewModel.selectCategoryComplete(selectPackages)
                    closeCategoryBottomSheet()
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
                    viewModel.lockTime()
                    // 차단 대상이 없으면 lockTime 이 선택 시트를 연다. 그 위에서 시간 시트를 닫고
                    // 잠금 화면으로 넘기면 방금 연 시트를 덮어버린다. 웹사이트만 고른 잠금도
                    // 대상이 있는 잠금이므로 같은 경로를 탄다.
                    if (uiState.hasSelectedLockTargets()) {
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
            KeepTopAppBar(
                title = { },
                actions = {
                    KeepIconButton(onClick = onNavigateMenu) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_format_list_bulleted_24),
                            contentDescription = stringResource(R.string.cd_open_menu),
                            tint = KeepTheme.colors.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = {
            Box(modifier = Modifier.fillMaxSize()) {
                KeepSnackbarHost(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding(),
                    hostState = snackBarHostState,
                )
            }
        },
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        val hasFirstPromiseResumeCard = uiState.firstPromiseResumeCard != null
        val hasWebBlockingWarning = currentWebsiteBlockingWarning(
            hasWebsiteTargets = uiState.selectedWebDomains.isNotEmpty(),
        ) != null
        val homeCard = decideHomeCard(
            isKeep = uiState.isKeep,
            hasActiveTimedLock = uiState.hasActiveTimedLock,
            hasFirstPromiseResumeCard = hasFirstPromiseResumeCard,
            showFirstLockActivationCta = uiState.showFirstLockActivationCta,
            usageInsightCard = uiState.usageInsightCard,
            hasWebBlockingWarning = hasWebBlockingWarning,
        )
        val topContent: @Composable ColumnScope.() -> Unit = {
        CategoryButton(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            onClick = viewModel::showCategoryBottomSheet,
            enabled = !uiState.isKeep && !uiState.hasActiveTimedLock,
            categorySize = uiState.selectedAppPackage.size,
            websiteSize = uiState.selectedWebDomains.size,
        )
        // 수동 잠금은 잠금 화면으로 넘어가지 않는다. 홈에 머무는 사용자도 웹 차단이
        // 서지 못했다는 사실을 같은 방식으로 알아야 한다.
        // 잠금 화면에서는 20dp 여백을 가진 부모가 감싸주지만 홈에는 그런 부모가 없다.
        // 여기서 직접 주지 않으면 둥근 경고 배경이 화면 끝에 닿는다.
        WebsiteBlockingUnavailableBanner(
            modifier = Modifier.padding(horizontal = 20.dp),
            hasWebsiteTargets = uiState.selectedWebDomains.isNotEmpty(),
            // 권한을 다시 받아도 필터가 저절로 서지는 않는다. 판정 효과는 판정값이 바뀔 때만
            // 도는데 잠금은 그대로이므로 아무 일도 일어나지 않는다. 아무것도 서 있지 않을 때
            // 다시 세우는 길이 이미 있으니(재개 재확인) 그 길을 여기서 한 번 두드린다.
            onConsentGranted = { resumeCount += 1 },
        )
        // 카드는 한 번에 한 장만 노출한다. 선택되지 않은 후보는 조건이 유지되는 한 다음
        // 방문에서 다시 올라온다.
        val cardModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
        when (homeCard) {
            HomeCard.FirstPromiseResume -> FirstPromiseResumeCard(
                state = uiState.firstPromiseResumeCard,
                onActivate = viewModel::activateFirstPromiseResumeCard,
                modifier = cardModifier,
            )
            HomeCard.FirstLockActivation -> FirstLockActivationCta(
                modifier = cardModifier,
                onClick = { viewModel.changeIsKeep() },
            )
            HomeCard.UsageInsight -> UsageInsightCard(
                state = uiState.usageInsightCard,
                onCtaClick = viewModel::onUsageInsightCtaClick,
                onDismiss = viewModel::onUsageInsightDismiss,
                modifier = cardModifier,
            )
            HomeCard.None -> Unit
        }
        }
        val mainControls: @Composable (Modifier) -> Unit = { modifier ->
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(
                        space = 20.dp,
                        alignment = Alignment.CenterVertically,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val (image, message) = if (uiState.isKeep) {
                        R.drawable.kepp_icon to stringResource(R.string.keep_turned_off)
                    } else {
                        R.drawable.disable_logo to stringResource(R.string.keep_turned_on)
                    }
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            modifier = Modifier
                                .sizeIn(minHeight = 100.dp, minWidth = 100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .testTag(HOME_KEEP_TOGGLE_TOUCH_SHORTCUT_TEST_TAG)
                                .clearAndSetSemantics { }
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!uiState.isKeep && !uiState.hasSelectedLockTargets()) {
                                        viewModel.changeIsKeep()
                                    } else {
                                        viewModel.showSnackBar(message)
                                        viewModel.changeIsKeep()
                                    }
                                },
                            painter = painterResource(id = image),
                            contentDescription = null,
                        )
                        if (uiState.isKeep) {
                            // 아이콘 중심에서 바깥으로 펼쳐지는 장식. matchParentSize 로 아이콘
                            // 크기를 따라가 측정에는 관여하지 않고, requiredSize 로 아이콘보다
                            // 크게 그려 중심을 공유한 채 밖으로 번진다. 아이콘보다 나중에 두어
                            // 위에 얹히며, 터치는 잡지 않으므로 아이콘 클릭은 그대로 동작한다.
                            Box(modifier = Modifier.matchParentSize()) {
                                LottieAnimation(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .requiredSize(LOTTIE_BURST_SIZE),
                                    composition = composition,
                                    iterations = LottieConstants.IterateForever,
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        val keepSwitchDescription = stringResource(
                            if (uiState.isKeep) R.string.keep_on_status
                            else R.string.keep_off_status,
                        )
                        KeepSwitch(
                            modifier = Modifier.semantics {
                                contentDescription = keepSwitchDescription
                            },
                            checked = uiState.isKeep,
                            onCheckedChange = {
                                if (!uiState.isKeep && !uiState.hasSelectedLockTargets()) {
                                    viewModel.changeIsKeep()
                                } else {
                                    viewModel.showSnackBar(message)
                                    viewModel.changeIsKeep()
                                }
                            },
                        )
                        Image(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    shape = RoundedCornerShape(8.dp),
                                    color = KeepTheme.colors.onSecondary,
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    onClick = viewModel::showTimeBottomSheet,
                                    enabled = !uiState.isKeep,
                            )
                                .padding(4.dp),
                            painter = painterResource(id = R.drawable.timer_outline),
                            contentDescription = stringResource(R.string.cd_open_timer),
                        )
                    }
                }
            }
        }
        val bottomContent: @Composable () -> Unit = {
            Column {
                // 배너와의 간격은 배너가 소유한다. 여기서 또 주면 두 번 들어간다.
                ContentDescription(
                    modifier = Modifier.fillMaxWidth(),
                    isKeep = uiState.isKeep,
                    startTime = uiState.startTime,
                    lockTargetKind = uiState.lockTargetKind(),
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
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // 위쪽 카드는 개수(재개 카드·사용 인사이트)와 글꼴 크기에 따라 높이가 크게 달라진다.
            // 항상 스크롤 가능한 표면으로 두어 넘칠 때 잘리는 대신 스크롤되게 하고, 하단 문구와
            // 배너는 그 바깥에 형제로 두어 바닥에 고정한다.
            //
            // 넘치면 잠금 컨트롤도 함께 밀려 스크롤해야 나온다. 상단이 한 덩어리(경고 배너 또는
            // 카드 한 장)로 제한되어 있어 기본 글꼴에서는 넘치지 않는다는 전제 위에 서 있다.
            // 제스처를 화면 하나로 통일하기 위해 그 전제를 택했다.
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val viewportHeight = maxHeight
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        // 카드가 적어 콘텐츠가 짧아도 표면이 화면을 채우게 해, 메인 컨트롤이
                        // 위로 딸려 올라오지 않고 하단 묶음 바로 위에 남는다.
                        .heightIn(min = viewportHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column { topContent() }
                    mainControls(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MAIN_CONTROLS_MIN_HEIGHT)
                            .testTag(HOME_MAIN_CONTROLS_TEST_TAG),
                    )
                    // 바닥에 붙는 0높이 앵커. SpaceBetween 은 첫 자식을 맨 위, 마지막 자식을 맨
                    // 아래에 붙이고 남은 높이를 자식 사이에 균등 배분한다. 앵커가 없으면 남는
                    // 높이가 카드와 컨트롤 사이 한 곳에 몰려 컨트롤이 바닥에 붙는다. 앵커를 두면
                    // 간격이 둘로 나뉘어 컨트롤 위아래 공백이 1:1이 된다.
                    Spacer(Modifier)
                }
            }
            bottomContent()
        }
    }
}

/**
 * 잠금 컨트롤이 지키는 최소 높이.
 *
 * 아이콘·간격·스위치 행(100+20+40)이 실제로 쓰는 높이는 160dp 이고 글자가 없어 글꼴 크기를
 * 키워도 자라지 않는다. 나머지 100dp 는 컨트롤이 화면 중앙에 놓이도록 하는 숨 쉴 자리다.
 */
private val MAIN_CONTROLS_MIN_HEIGHT = 260.dp

/**
 * 아이콘(최소 100dp) 중심에서 바깥으로 번지는 크기.
 *
 * 컨트롤 영역 안에 온전히 담기는 한계는 `영역 높이 - 60dp`다. 아이콘·간격·스위치 행(100+20+40)이
 * 세로 중앙에 놓여 아이콘 중심이 영역 중앙보다 30dp 위에 오기 때문이다. 지금은 260dp 영역에
 * 280dp를 그리므로 위로 40dp가 넘쳐 카드 쪽으로 번진다. Box는 클리핑하지 않으므로 잘리지 않고,
 * 장식이 아이콘 위에 얹히는 연출이라 의도된 범위다.
 */
private val LOTTIE_BURST_SIZE = 280.dp

private const val HOME_MAIN_CONTROLS_TEST_TAG = "home_main_controls"
private const val HOME_KEEP_TOGGLE_TOUCH_SHORTCUT_TEST_TAG = "home_keep_toggle_touch_shortcut"

@Composable
private fun FirstLockActivationCta(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    KeepCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
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
    KeepCard(
        onClick = onClick,
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = talkBackSummary
            },
        shape = RoundedCornerShape(16.dp),
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
    KeepCard(
        modifier = modifier.testTag("home_status_cta_card"),
        shape = RoundedCornerShape(18.dp),
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
