package com.uiery.keep.feature.onboarding.select

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.uiery.kds.KeepButton
import com.uiery.keep.R
import com.uiery.keep.feature.home.component.CategoryBottomSheetContent
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.compose.collectAsState
import androidx.compose.ui.res.stringResource
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.theme.KeepTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectAppScreen(
    modifier: Modifier = Modifier,
    viewModel: SelectAppViewModel = hiltViewModel(),
    onNavigateHome: () -> Unit,
) {
    val uiState by viewModel.collectAsState()
    val categoryBottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.picker_guide_lottie)
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.onStepViewed()
    }

    if (uiState.isShowCategoryBottomSheet) {
        KeepModalBottomSheet(
            sheetState = categoryBottomSheetState,
            onDismissRequest = viewModel::hideCategoryBottomSheet,
        ) {
            CategoryBottomSheetContent(
                storeSelectApps = emptySet(),
                onComplete = { selectPackages ->
                    viewModel.selectCategoryComplete(selectPackages)
                    coroutineScope.launch {
                        categoryBottomSheetState.hide()
                    }.invokeOnCompletion {
                        if (!categoryBottomSheetState.isVisible) {
                            viewModel.hideCategoryBottomSheet()
                            onNavigateHome()
                        }
                    }
                },
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
        ) {
            Text(
                modifier = Modifier.padding(top = 36.dp),
                text = stringResource(id = R.string.select_apps_prompt),
                fontWeight = FontWeight.SemiBold,
                lineHeight = 28.sp,
                fontSize = 22.sp,
                color = KeepTheme.colors.onSurfaceVariant,
            )
            Text(
                modifier = Modifier.padding(top = 14.dp),
                text = stringResource(id = R.string.app_lock_info),
                color = KeepTheme.colors.surfaceVariant,
            )
            LottieAnimation(
                modifier = modifier.weight(1f),
                composition = composition,
                iterations = LottieConstants.IterateForever,
            )
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.select_apps_button),
                onClick = viewModel::showCategoryBottomSheet,
            )
        }
    }
}
