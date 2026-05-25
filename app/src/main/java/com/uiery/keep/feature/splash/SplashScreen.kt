package com.uiery.keep.feature.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = hiltViewModel(),
    onNavigateHome: () -> Unit,
    onNavigateOnboarding: () -> Unit,
    onNavigateLock: (lockTime: String?, Boolean) -> Unit,
) {
    viewModel.collectSideEffect { effect ->
        withContext(Dispatchers.Main.immediate) {
            when (effect) {
                is SplashSideEffect.MoveToHome -> onNavigateHome()
                is SplashSideEffect.MoveToOnboarding -> onNavigateOnboarding()
                is SplashSideEffect.MoveToLock -> onNavigateLock(effect.lockTime, effect.isRoutine)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color = KeepTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            modifier = Modifier.clip(CircleShape),
            painter = painterResource(R.drawable.kepp_icon),
            contentDescription = null,
        )
    }
}
