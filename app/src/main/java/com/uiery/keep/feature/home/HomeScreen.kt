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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.app.Activity
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.KeepSnackBar
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.analytics.AdPlacementMetadata
import com.uiery.keep.analytics.KeepAnalyticsScreen
import com.uiery.keep.analytics.TrackedBannerAd
import com.uiery.keep.feature.home.component.CategoryBottomSheetContent
import com.uiery.keep.feature.home.component.CategoryButton
import com.uiery.keep.feature.home.component.ContentDescription
import com.uiery.keep.feature.home.component.KeepSwitch
import com.uiery.keep.feature.home.component.TimeBottomSheetContent
import com.uiery.keep.feature.onboarding.permission.component.PermissionSettingDialog
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
        }
    }

    LaunchedEffect(Unit) {
        viewModel.analyticsHomeScreen()
        if (!hasAccessibilityPermission(context)) {
            openAlertDialog = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.maybeDrainReviewFlag(activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                            contentDescription = null,
                            tint = KeepTheme.colors.primary,
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
                                        viewModel.showSnackBar(message)
                                        viewModel.changeIsKeep()
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
                                    viewModel.showSnackBar(message)
                                    viewModel.changeIsKeep()
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
                            placement = "home_bottom",
                            adUnitId = "ca-app-pub-1537867411423705/5120253017",
                        ),
                    )
                }
            }
        }
    }
}
