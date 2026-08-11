package com.uiery.keep.ui.component

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.keep.R
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uiery.kds.KeepModalBottomSheet
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 목록 위의 손짓이 시트를 끌고 다니지 않는지 본다.
 *
 * 이 시트의 목록은 언제나 맨 위에서 시작한다. 그대로 두면 처음 아래로 쓸어내리는 동작이
 * 곧바로 시트를 끌어내려, 목록을 읽으려던 손짓이 닫으려는 손짓으로 읽힌다. 손가락을 떼고
 * 나면 시트가 제자리로 돌아오므로 제스처가 끝난 뒤에 재면 이 문제는 보이지 않는다.
 */
@RunWith(AndroidJUnit4::class)
class CategorySheetScrollBehaviorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun draggingTheListDoesNotCarryTheSheetWithIt() {
        val apps = (1..40).map { index ->
            AppInfo(
                isChecked = false,
                packageName = "com.example.app$index",
                appName = "App $index",
                appIcon = BitmapDrawable(
                    Resources.getSystem(),
                    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                ),
            )
        }
        lateinit var offsetReader: () -> Float

        composeRule.setContent {
            KeepTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                offsetReader = { runCatching { sheetState.requireOffset() }.getOrDefault(-1f) }
                LaunchedEffect(Unit) { sheetState.show() }
                KeepModalBottomSheet(
                    sheetState = sheetState,
                    onDismissRequest = { },
                ) {
                    CategoryBottomSheetLoadedContent(
                        modifier = Modifier.fillMaxSize(),
                        apps = apps,
                        storeSelectApps = emptySet(),
                        onComplete = { },
                        websiteSelectionEnabled = true,
                        onCompleteTargets = { _, _ -> },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val list = composeRule.onNodeWithTag("category_app_row_com.example.app1")
        val atRest = offsetReader()
        assertEquals("시트가 완전히 펼쳐진 상태에서 시작해야 한다", 0f, atRest, 0.5f)

        // 목록 맨 위에서 아래로 끄는 중. 손가락을 떼기 전에 읽는다.
        list.performTouchInput { down(center) }
        list.performTouchInput { moveBy(Offset(0f, 120f)) }
        composeRule.waitForIdle()
        val duringDragDownAtTop = offsetReader()
        list.performTouchInput { up() }
        composeRule.waitForIdle()

        // 위로 끄는 중. 목록이 스크롤할 여유가 있으므로 원래도 시트는 가만히 있어야 한다.
        list.performTouchInput { down(center) }
        list.performTouchInput { moveBy(Offset(0f, -120f)) }
        composeRule.waitForIdle()
        val duringDragUp = offsetReader()
        list.performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(
            "목록 맨 위에서 아래로 끌어도 시트는 따라오면 안 된다 (가드 전 측정값: 99px)",
            0f,
            duringDragDownAtTop,
            0.5f,
        )
        assertEquals("위로 끌 때 시트가 움직이면 안 된다", 0f, duringDragUp, 0.5f)
    }

    /**
     * 목록을 위쪽 끝까지 밀어붙여도 시트가 닫히면 안 된다.
     *
     * 가드를 떼고 실기기에서 재면 offset 이 2320px 까지 밀려 시트가 화면에서 사라지고
     * 닫힘 요청이 5번 발생했다. 목록을 처음으로 되돌리려던 손짓이 시트를 닫아버리는 셈이다.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun scrollingToEitherEndOfTheListNeverDismissesTheSheet() {
        val apps = (1..12).map { index ->
            AppInfo(
                isChecked = false,
                packageName = "com.example.app$index",
                appName = "App $index",
                appIcon = BitmapDrawable(
                    Resources.getSystem(),
                    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                ),
            )
        }
        var dismissRequested = 0
        lateinit var readVisible: () -> Boolean
        lateinit var offsetReader: () -> Float

        composeRule.setContent {
            KeepTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                offsetReader = { runCatching { sheetState.requireOffset() }.getOrDefault(-1f) }
                readVisible = { sheetState.isVisible }
                LaunchedEffect(Unit) { sheetState.show() }
                KeepModalBottomSheet(
                    sheetState = sheetState,
                    onDismissRequest = { dismissRequested += 1 },
                ) {
                    CategoryBottomSheetLoadedContent(
                        modifier = Modifier.fillMaxSize(),
                        apps = apps,
                        storeSelectApps = emptySet(),
                        onComplete = { },
                        websiteSelectionEnabled = true,
                        onCompleteTargets = { _, _ -> },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val list = composeRule.onNodeWithTag("category_app_list")
        repeat(5) {
            list.performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        assertTrue("목록 아래 끝에서 시트가 닫히면 안 된다", readVisible())

        repeat(5) {
            list.performTouchInput { swipeDown() }
            composeRule.waitForIdle()
        }
        assertTrue(
            "목록 위 끝까지 밀어붙여도 시트가 닫히면 안 된다 (offset=${offsetReader()})",
            readVisible(),
        )
        assertEquals("닫힘 요청이 발생하면 안 된다", 0, dismissRequested)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun theWebsiteListAlsoKeepsTheSheetStill() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var offsetReader: () -> Float

        composeRule.setContent {
            KeepTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                offsetReader = { runCatching { sheetState.requireOffset() }.getOrDefault(-1f) }
                LaunchedEffect(Unit) { sheetState.show() }
                KeepModalBottomSheet(sheetState = sheetState, onDismissRequest = { }) {
                    CategoryBottomSheetLoadedContent(
                        modifier = Modifier.fillMaxSize(),
                        apps = emptyList(),
                        storeSelectApps = emptySet(),
                        onComplete = { },
                        websiteSelectionEnabled = true,
                        onCompleteTargets = { _, _ -> },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.lock_target_websites)).performClick()
        composeRule.waitForIdle()

        val list = composeRule.onNodeWithTag("website_preset_youtube")
        list.performTouchInput { down(center) }
        list.performTouchInput { moveBy(Offset(0f, 120f)) }
        composeRule.waitForIdle()
        val duringDragDown = offsetReader()
        list.performTouchInput { up() }

        assertEquals("웹사이트 목록에서도 시트는 따라오면 안 된다", 0f, duringDragDown, 0.5f)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun theCompleteButtonStaysOnScreenWhileAppsAreLoading() {
        composeRule.setContent {
            KeepTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                LaunchedEffect(Unit) { sheetState.show() }
                KeepModalBottomSheet(sheetState = sheetState, onDismissRequest = { }) {
                    CategoryBottomSheetLoadedContent(
                        modifier = Modifier.fillMaxSize(),
                        apps = emptyList(),
                        storeSelectApps = emptySet(),
                        isLoading = true,
                        onComplete = { },
                        websiteSelectionEnabled = true,
                        onCompleteTargets = { _, _ -> },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 로딩 가지만 weight 없이 fillMaxSize 라 남은 높이를 전부 삼켰고, 완료 버튼이
        // 화면 밖으로 밀려 bounds 가 전부 0 이었다.
        val button = composeRule.onNodeWithTag("category_selection_complete")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "로딩 중에도 완료 버튼이 화면 안에 있어야 한다 (bounds=$button)",
            button.height > 0f && button.bottom > 0f,
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun theSheetCanStillBeDraggedFromOutsideTheList() {
        lateinit var offsetReader: () -> Float

        composeRule.setContent {
            KeepTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                offsetReader = { runCatching { sheetState.requireOffset() }.getOrDefault(-1f) }
                LaunchedEffect(Unit) { sheetState.show() }
                KeepModalBottomSheet(
                    sheetState = sheetState,
                    onDismissRequest = { },
                ) {
                    CategoryBottomSheetLoadedContent(
                        modifier = Modifier.fillMaxSize(),
                        apps = emptyList(),
                        storeSelectApps = emptySet(),
                        onComplete = { },
                        websiteSelectionEnabled = true,
                        onCompleteTargets = { _, _ -> },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 목록 바깥(선택 개수 요약 줄)에서 끌면 닫는 길은 그대로 남아 있어야 한다.
        val outsideList = composeRule.onNodeWithTag("lock_targets_summary")
        outsideList.performTouchInput { down(center) }
        outsideList.performTouchInput { moveBy(Offset(0f, 150f)) }
        composeRule.waitForIdle()
        val duringDragOutsideList = offsetReader()
        outsideList.performTouchInput { up() }
        composeRule.waitForIdle()

        assertTrue(
            "목록 바깥에서는 시트를 끌어내릴 수 있어야 한다 (측정값: $duringDragOutsideList)",
            duringDragOutsideList > 0f,
        )
    }
}
