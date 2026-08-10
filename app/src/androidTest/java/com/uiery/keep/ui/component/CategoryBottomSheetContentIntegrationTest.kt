package com.uiery.keep.ui.component

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.appselection.BlockExemptPackages
import com.uiery.keep.appselection.SensitiveAppRole
import com.uiery.keep.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryBottomSheetContentIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectionStateUpdatesVisibleChecksAndCompletionPayloadAfterFiltering() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completedSelections = mutableListOf<Set<String>>()
        val apps = listOf(
            testApp(packageName = "com.example.alpha", appName = "Alpha Focus"),
            testApp(packageName = "com.example.beta", appName = "Beta Notes"),
        )

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = apps,
                    storeSelectApps = setOf("com.example.beta"),
                    onComplete = { completedSelections += it },
                )
            }
        }

        composeRule.onNodeWithTag("category_app_row_com.example.beta").assertIsOn()
        composeRule.onNodeWithTag("category_app_row_com.example.alpha").assertIsOff()

        composeRule.onNodeWithText(context.getString(R.string.search)).performTextInput("Alpha")
        composeRule.onNodeWithText("Alpha Focus").performClick()
        composeRule.onNodeWithTag("category_app_row_com.example.alpha").assertIsOn()

        composeRule.onNodeWithTag("category_selection_complete").performClick()

        assertEquals(listOf(setOf("com.example.alpha", "com.example.beta")), completedSelections)
    }

    /**
     * The confirmation exists because 1.7.12 found "select all" sweeping device-role apps in with no
     * warning. Removing them from the picker fixed the silence and broke deliberate blocking, so the
     * guard has to be the dialog — and it has to hold on the select-all path specifically.
     */
    @Test
    fun selectAllRoutesSensitiveAppsThroughTheConfirmationInsteadOfSavingSilently() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completedSelections = mutableListOf<Set<String>>()
        val apps = listOf(
            testApp(packageName = "com.example.alpha", appName = "Alpha Focus"),
            testApp(packageName = SMS_PACKAGE, appName = "Messages"),
        )

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = apps,
                    exemptPackages = sensitiveMessagingExemptPackages(),
                    storeSelectApps = emptySet(),
                    onComplete = { completedSelections += it },
                )
            }
        }

        composeRule.onNodeWithTag("category_select_all_row").performClick()
        composeRule.onNodeWithTag("category_selection_complete").performClick()

        composeRule.onNodeWithText(context.getString(R.string.sensitive_block_confirm_title))
            .assertIsDisplayed()
        assertEquals(emptyList<Set<String>>(), completedSelections)

        composeRule.onNodeWithText(context.getString(R.string.sensitive_block_confirm_include)).performClick()

        assertEquals(listOf(setOf("com.example.alpha", SMS_PACKAGE)), completedSelections)
    }

    /** Tapping one sensitive app directly must reach the same confirmation as select all. */
    @Test
    fun individuallyTappingASensitiveAppAlsoRoutesThroughTheConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completedSelections = mutableListOf<Set<String>>()
        val apps = listOf(
            testApp(packageName = "com.example.alpha", appName = "Alpha Focus"),
            testApp(packageName = SMS_PACKAGE, appName = "Messages"),
        )

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = apps,
                    exemptPackages = sensitiveMessagingExemptPackages(),
                    storeSelectApps = emptySet(),
                    onComplete = { completedSelections += it },
                )
            }
        }

        composeRule.onNodeWithTag("category_app_row_$SMS_PACKAGE").performClick()
        composeRule.onNodeWithTag("category_selection_complete").performClick()

        composeRule.onNodeWithText(context.getString(R.string.sensitive_block_confirm_title))
            .assertIsDisplayed()
        assertEquals(emptyList<Set<String>>(), completedSelections)
    }

    /** Declining still saves — the user asked to save, they only declined the sensitive apps. */
    @Test
    fun decliningTheConfirmationSavesEverythingExceptTheSensitiveApps() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completedSelections = mutableListOf<Set<String>>()
        val apps = listOf(
            testApp(packageName = "com.example.alpha", appName = "Alpha Focus"),
            testApp(packageName = SMS_PACKAGE, appName = "Messages"),
        )

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = apps,
                    exemptPackages = sensitiveMessagingExemptPackages(),
                    storeSelectApps = emptySet(),
                    onComplete = { completedSelections += it },
                )
            }
        }

        composeRule.onNodeWithTag("category_select_all_row").performClick()
        composeRule.onNodeWithTag("category_selection_complete").performClick()
        composeRule.onNodeWithText(context.getString(R.string.sensitive_block_confirm_exclude)).performClick()

        assertEquals(listOf(setOf("com.example.alpha")), completedSelections)
    }

    /** An ordinary selection saves straight through; a dialog on every save gets dismissed unread. */
    @Test
    fun ordinaryAppsSaveWithoutAConfirmation() {
        val completedSelections = mutableListOf<Set<String>>()
        val apps = listOf(testApp(packageName = "com.example.alpha", appName = "Alpha Focus"))

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = apps,
                    exemptPackages = sensitiveMessagingExemptPackages(),
                    storeSelectApps = emptySet(),
                    onComplete = { completedSelections += it },
                )
            }
        }

        composeRule.onNodeWithTag("category_app_row_com.example.alpha").performClick()
        composeRule.onNodeWithTag("category_selection_complete").performClick()

        assertEquals(listOf(setOf("com.example.alpha")), completedSelections)
    }

    private fun sensitiveMessagingExemptPackages() = BlockExemptPackages(
        sensitiveRoles = mapOf(SMS_PACKAGE to SensitiveAppRole.MESSAGING),
    )

    @Test
    fun selectingAnotherAppDoesNotMoveItAboveInitiallyUnselectedRows() {
        val apps = listOf(
            testApp(packageName = "com.example.alpha", appName = "Alpha Focus"),
            testApp(packageName = "com.example.beta", appName = "Beta Notes"),
            testApp(packageName = "com.example.gamma", appName = "Gamma Browser"),
        )

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = apps,
                    storeSelectApps = setOf("com.example.beta"),
                    onComplete = { },
                )
            }
        }

        assertAppearsBefore(
            firstTag = "category_app_row_com.example.beta",
            secondTag = "category_app_row_com.example.alpha",
        )
        assertAppearsBefore(
            firstTag = "category_app_row_com.example.alpha",
            secondTag = "category_app_row_com.example.gamma",
        )

        composeRule.onNodeWithTag("category_app_row_com.example.gamma").performClick()
        composeRule.waitForIdle()

        assertAppearsBefore(
            firstTag = "category_app_row_com.example.beta",
            secondTag = "category_app_row_com.example.alpha",
        )
        assertAppearsBefore(
            firstTag = "category_app_row_com.example.alpha",
            secondTag = "category_app_row_com.example.gamma",
        )
    }

    @Test
    fun parentSelectionUpdatesWhileSheetIsOpenDoNotResortOrResetCurrentEdits() {
        val parentSelection = mutableStateOf(setOf("com.example.beta"))
        val apps = listOf(
            testApp(packageName = "com.example.alpha", appName = "Alpha Focus"),
            testApp(packageName = "com.example.beta", appName = "Beta Notes"),
            testApp(packageName = "com.example.gamma", appName = "Gamma Browser"),
        )

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = apps,
                    storeSelectApps = parentSelection.value,
                    onComplete = { },
                )
            }
        }

        composeRule.onNodeWithTag("category_app_row_com.example.gamma").performClick()
        composeRule.onNodeWithTag("category_app_row_com.example.gamma").assertIsOn()
        composeRule.runOnIdle {
            parentSelection.value = setOf("com.example.beta", "com.example.gamma")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("category_app_row_com.example.gamma").assertIsOn()
        assertAppearsBefore(
            firstTag = "category_app_row_com.example.beta",
            secondTag = "category_app_row_com.example.alpha",
        )
        assertAppearsBefore(
            firstTag = "category_app_row_com.example.alpha",
            secondTag = "category_app_row_com.example.gamma",
        )
    }

    @Test
    fun appAndSelectAllRowsKeepComfortableVerticalPaddingAfterCheckboxSemanticsFix() {
        val apps = listOf(
            testApp(packageName = "com.example.alpha", appName = "Alpha Focus"),
            testApp(packageName = "com.example.beta", appName = "Beta Notes"),
        )

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = apps,
                    storeSelectApps = setOf("com.example.beta"),
                    onComplete = { },
                )
            }
        }

        composeRule.onNodeWithTag("category_select_all_row").assertHeightIsAtLeast(50.dp)
        composeRule.onNodeWithTag("category_app_row_com.example.alpha").assertHeightIsAtLeast(50.dp)
        composeRule.onNodeWithTag("category_app_row_com.example.beta").assertHeightIsAtLeast(50.dp)
    }

    @Test
    fun allAppsToggleClearsAndRestoresVisibleCheckboxState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completedSelections = mutableListOf<Set<String>>()
        val apps = listOf(
            testApp(packageName = "com.example.alpha", appName = "Alpha Focus"),
            testApp(packageName = "com.example.beta", appName = "Beta Notes"),
        )

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = apps,
                    storeSelectApps = emptySet(),
                    onComplete = { completedSelections += it },
                )
            }
        }

        composeRule.onNodeWithTag("category_select_all_row").assertIsOff()
        composeRule.onNodeWithTag("category_select_all_row").performClick()
        composeRule.onNodeWithTag("category_select_all_row").assertIsOn()
        composeRule.onNodeWithTag("category_app_row_com.example.alpha").assertIsOn()
        composeRule.onNodeWithTag("category_app_row_com.example.beta").assertIsOn()

        composeRule.onNodeWithTag("category_select_all_row").performClick()
        composeRule.onNodeWithTag("category_select_all_row").assertIsOff()
        composeRule.onNodeWithTag("category_app_row_com.example.alpha").assertIsOff()
        composeRule.onNodeWithTag("category_app_row_com.example.beta").assertIsOff()

        composeRule.onNodeWithTag("category_selection_complete").performClick()

        assertEquals(listOf(emptySet<String>()), completedSelections)
    }

    @Test
    fun appRowsExposeSingleCheckboxSemanticsWithLocalizedSelectionState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apps = listOf(
            testApp(packageName = "com.example.alpha", appName = "Alpha Focus"),
            testApp(packageName = "com.example.beta", appName = "Beta Notes"),
        )

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = apps,
                    storeSelectApps = setOf("com.example.beta"),
                    onComplete = { },
                )
            }
        }

        composeRule.onNodeWithTag("category_app_row_com.example.alpha")
            .assert(hasCheckboxRole())
            .assert(hasToggleableState(ToggleableState.Off))
            .assert(hasStateDescription(context.getString(R.string.cd_tab_not_selected)))

        composeRule.onNodeWithTag("category_app_row_com.example.beta")
            .assert(hasCheckboxRole())
            .assert(hasToggleableState(ToggleableState.On))
            .assert(hasStateDescription(context.getString(R.string.cd_tab_selected)))
    }

    @Test
    fun allAppsRowExposesCheckboxSemanticsWithLocalizedSelectionState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apps = listOf(
            testApp(packageName = "com.example.alpha", appName = "Alpha Focus"),
            testApp(packageName = "com.example.beta", appName = "Beta Notes"),
        )

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = apps,
                    storeSelectApps = apps.map { it.packageName }.toSet(),
                    onComplete = { },
                )
            }
        }

        composeRule.onNodeWithTag("category_select_all_row")
            .assert(hasCheckboxRole())
            .assert(hasToggleableState(ToggleableState.On))
            .assert(hasStateDescription(context.getString(R.string.cd_tab_selected)))
    }

    @Test
    fun recommendedWebsiteAddsItsDomainsAndThenReportsItselfAsAdded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completedTargets = mutableListOf<Pair<Set<String>, Set<String>>>()

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = emptyList(),
                    storeSelectApps = emptySet(),
                    onComplete = { },
                    websiteSelectionEnabled = true,
                    onCompleteTargets = { apps, domains ->
                        completedTargets += apps to domains
                    },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.lock_target_websites)).performClick()
        composeRule.onNodeWithTag("website_preset_youtube").performClick()
        composeRule.waitForIdle()

        // 담긴 뒤에는 담을 것이 남지 않았다고 말해야 한다. 체크박스였을 때는 도메인 하나만
        // 지워도 체크가 풀려 실제로 막히고 있는 것과 표시가 어긋났다.
        composeRule.onNodeWithTag("website_preset_youtube")
            .assertTextContains(context.getString(R.string.website_lock_preset_added))

        composeRule.onNodeWithTag("category_selection_complete").performClick()
        assertEquals(
            listOf(emptySet<String>() to setOf("youtube.com", "youtu.be")),
            completedTargets,
        )
    }

    @Test
    fun removingOneDomainOfAPresetKeepsTheOtherAndLetsThePresetRefillIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completedTargets = mutableListOf<Pair<Set<String>, Set<String>>>()

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = emptyList(),
                    storeSelectApps = emptySet(),
                    onComplete = { },
                    websiteSelectionEnabled = true,
                    onCompleteTargets = { apps, domains ->
                        completedTargets += apps to domains
                    },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.lock_target_websites)).performClick()
        composeRule.onNodeWithTag("website_preset_youtube").performClick()
        composeRule.waitForIdle()

        // 삭제 버튼은 행마다 같은 글자라 도메인이 붙은 설명으로만 구분된다.
        composeRule
            .onNodeWithContentDescription(
                context.getString(R.string.website_lock_delete_domain, "youtu.be"),
            )
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("category_selection_complete").performClick()
        assertEquals(
            listOf(emptySet<String>() to setOf("youtube.com")),
            completedTargets,
        )
    }

    @Test
    fun parentWebDomainUpdatesWhileSheetIsOpenDoNotResetCurrentEdits() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parentDomains = mutableStateOf(setOf("stored.example.org"))
        val completedTargets = mutableListOf<Pair<Set<String>, Set<String>>>()

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = emptyList(),
                    storeSelectApps = emptySet(),
                    onComplete = { },
                    websiteSelectionEnabled = true,
                    storeSelectedWebDomains = parentDomains.value,
                    onCompleteTargets = { apps, domains -> completedTargets += apps to domains },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.lock_target_websites)).performClick()
        composeRule.onNodeWithTag("website_domain_input").performTextInput("typed.example.org")
        composeRule.onNodeWithTag("website_domain_add").performClick()
        composeRule.waitForIdle()

        // 앱 선택은 부모 갱신에 편집이 지워지지 않는다. 웹사이트만 다르게 굴면 같은 시트
        // 안에서 두 선택이 같은 사건에 다르게 반응하게 된다.
        composeRule.runOnIdle { parentDomains.value = setOf("stored.example.org", "other.example.org") }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("category_selection_complete").performClick()
        assertEquals(
            listOf(emptySet<String>() to setOf("typed.example.org", "stored.example.org")),
            completedTargets,
        )
    }

    @Test
    fun reopeningTheSheetSeedsFromTheStoreAndDropsAbandonedEdits() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val showSheet = mutableStateOf(true)
        val completedTargets = mutableListOf<Pair<Set<String>, Set<String>>>()

        composeRule.setContent {
            KeepTheme {
                if (showSheet.value) {
                    CategoryBottomSheetLoadedContent(
                        apps = emptyList(),
                        storeSelectApps = emptySet(),
                        onComplete = { },
                        websiteSelectionEnabled = true,
                        storeSelectedWebDomains = setOf("stored.example.org"),
                        onCompleteTargets = { apps, domains -> completedTargets += apps to domains },
                    )
                }
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.lock_target_websites)).performClick()
        composeRule.onNodeWithTag("website_domain_input").performTextInput("abandoned.example.org")
        composeRule.onNodeWithTag("website_domain_add").performClick()
        composeRule.waitForIdle()

        // 루틴 선택 화면은 저장하지 않고 뒤로 갈 수 있다. 그때 버린 편집이 되살아나면
        // 사용자가 취소한 선택이 조용히 저장된다.
        composeRule.runOnIdle { showSheet.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { showSheet.value = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.lock_target_websites)).performClick()
        composeRule.onNodeWithTag("category_selection_complete").performClick()

        assertEquals(
            listOf(emptySet<String>() to setOf("stored.example.org")),
            completedTargets,
        )
    }

    @Test
    fun addButtonIsDisabledUntilSomethingIsTyped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = emptyList(),
                    storeSelectApps = emptySet(),
                    onComplete = { },
                    websiteSelectionEnabled = true,
                    onCompleteTargets = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.lock_target_websites)).performClick()
        composeRule.waitForIdle()

        // 빈 칸으로 누를 수 있으면 "형식이 맞지 않는다"는 엉뚱한 오류를 보게 된다.
        composeRule.onNodeWithTag("website_domain_add").assertIsNotEnabled()
        composeRule.onNodeWithTag("website_domain_input").performTextInput("typed.example.org")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("website_domain_add").assertIsEnabled()
    }

    @Test
    fun theDnsCaveatUsesTheSmallTextLineHeightNotTheInheritedBodyOne() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = emptyList(),
                    storeSelectApps = emptySet(),
                    onComplete = { },
                    websiteSelectionEnabled = true,
                    onCompleteTargets = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.lock_target_websites)).performClick()
        val caveat = context.getString(R.string.website_lock_dns_caveat)
        composeRule.onNodeWithTag("website_lock_list").performScrollToNode(hasText(caveat))
        composeRule.waitForIdle()

        // fontSize 만 지정하면 주변에서 상속된 bodyLarge 행간(22sp)이 12sp 글자에 그대로
        // 남아 줄당 22dp 가 된다. bodySmall(12/16) 이면 줄당 16dp 다.
        val density = context.resources.displayMetrics.density
        val heightDp = composeRule.onNodeWithText(caveat)
            .fetchSemanticsNode().size.height / density
        val lineCount = (heightDp / 16f).roundToInt()
        assertTrue(
            "주의 문구 행간이 스케일을 벗어났다: ${heightDp}dp / ${lineCount}줄",
            lineCount >= 1 && kotlin.math.abs(heightDp - lineCount * 16f) <= 2f,
        )
    }

    @Test
    fun addedDomainAppearsAboveTheRecommendationsSoTheInputHasVisibleFeedback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = emptyList(),
                    storeSelectApps = emptySet(),
                    onComplete = { },
                    websiteSelectionEnabled = true,
                    onCompleteTargets = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.lock_target_websites)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.website_lock_empty)).assertExists()

        composeRule.onNodeWithTag("website_domain_input").performTextInput("blocked.example.org")
        composeRule.onNodeWithTag("website_domain_add").performClick()
        composeRule.waitForIdle()

        // 추가한 도메인이 추천 목록 아래로 밀리면 입력한 결과를 스크롤해야만 볼 수 있다.
        val addedTop = composeRule.onNodeWithText("blocked.example.org")
            .fetchSemanticsNode().boundsInRoot.top
        val recommendedTop = composeRule.onNodeWithText(context.getString(R.string.website_lock_recommended))
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("추가한 도메인이 추천 목록보다 위에 있어야 한다", addedTop < recommendedTop)
    }

    @Test
    fun newestDomainStaysAtTheTopInsteadOfBeingSortedOutOfSight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            KeepTheme {
                CategoryBottomSheetLoadedContent(
                    apps = emptyList(),
                    storeSelectApps = emptySet(),
                    onComplete = { },
                    websiteSelectionEnabled = true,
                    onCompleteTargets = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.lock_target_websites)).performClick()
        composeRule.onNodeWithTag("website_domain_input").performTextInput("aaa.example.org")
        composeRule.onNodeWithTag("website_domain_add").performClick()
        composeRule.waitForIdle()
        // 알파벳순이라면 zzz 는 aaa 아래로 가서 방금 담은 것이 보이지 않는다.
        composeRule.onNodeWithTag("website_domain_input").performTextInput("zzz.example.org")
        composeRule.onNodeWithTag("website_domain_add").performClick()
        composeRule.waitForIdle()

        val newestTop = composeRule.onNodeWithText("zzz.example.org")
            .fetchSemanticsNode().boundsInRoot.top
        val previousTop = composeRule.onNodeWithText("aaa.example.org")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("방금 담은 도메인이 맨 위에 있어야 한다", newestTop < previousTop)
    }

    private fun hasCheckboxRole(): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private fun hasToggleableState(state: ToggleableState): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)

    private fun hasStateDescription(description: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description)

    private fun assertAppearsBefore(firstTag: String, secondTag: String) {
        val firstTop = composeRule.onNodeWithTag(firstTag).fetchSemanticsNode().boundsInRoot.top
        val secondTop = composeRule.onNodeWithTag(secondTag).fetchSemanticsNode().boundsInRoot.top
        assertTrue("$firstTag should appear before $secondTag", firstTop < secondTop)
    }

    private companion object {
        const val SMS_PACKAGE = "com.samsung.android.messaging"
    }

    private fun testApp(
        packageName: String,
        appName: String,
    ): AppInfo = AppInfo(
        isChecked = false,
        packageName = packageName,
        appName = appName,
        appIcon = BitmapDrawable(
            Resources.getSystem(),
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
        ),
    )
}
