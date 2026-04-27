package com.uiery.keep.feature.onboarding.intro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.uiery.kds.KeepButton
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R

@Composable
fun IntroScreen(
    modifier: Modifier = Modifier,
    viewModel: IntroViewModel = hiltViewModel(),
    onNavigatePermissionSetting: () -> Unit,
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.intro_lottie)
    )

    LaunchedEffect(Unit) {
        viewModel.onStepViewed()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KeepTheme.colors.background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                modifier = Modifier.padding(top = 48.dp),
                text = stringResource(id = R.string.intro_text),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = KeepTheme.colors.onSurfaceVariant,
            )
            LottieAnimation(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                composition = composition,
                iterations = LottieConstants.IterateForever,
            )
            KeepButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.start_button),
                onClick = {
                    viewModel.onContinue()
                    onNavigatePermissionSetting()
                },
            )
        }
    }
}
