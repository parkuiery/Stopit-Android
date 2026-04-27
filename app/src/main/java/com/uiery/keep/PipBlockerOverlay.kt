package com.uiery.keep

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.uiery.keep.analytics.AnalyticsBlockSource

class PipBlockerOverlay(
    private val context: Context,
    private val packageName: String,
    private val onClose: () -> Unit,
) {

    private var overlayView: FrameLayout? = null
    private var composeView: ComposeView? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun blockPipScreen() {
        // 기존 오버레이 제거
        removeOverlay()

        composeView = ComposeView(context).apply {
            setContent {
                BlockScreen(
                    packageName = packageName,
                    blockSource = AnalyticsBlockSource.MANUAL_KEEP,
                    routineId = null,
                ) {

                }
            }
        }

        // 새 오버레이 생성
        overlayView = FrameLayout(context).apply {
            //addView(composeView)
            setBackgroundColor(android.graphics.Color.BLACK) // 완전히 불투명한 검은색
        }

        // WindowManager.LayoutParams 설정
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            // 오버레이 권한이 필요한 타입
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 터치 이벤트 무시, 다른 화면 위에 표시
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // 오버레이 추가
        try {
            windowManager.addView(overlayView, params)
        } catch (e: SecurityException) {
            // 권한 확인 필요
            requestOverlayPermission()
        }
    }

    fun removeOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    internal fun requestOverlayPermission() {
        // 오버레이 권한 요청 인텐트
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }

    // 권한 확인 함수
    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // 이전 버전에서는 권한 확인 불필요
        }
    }
}
