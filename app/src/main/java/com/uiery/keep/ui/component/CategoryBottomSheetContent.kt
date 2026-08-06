package com.uiery.keep.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepButtonSize
import com.uiery.kds.KeepCircularProgressIndicator
import com.uiery.kds.KeepField
import com.uiery.kds.KeepRadioButton
import com.uiery.kds.KeepSegmentedControl
import com.uiery.kds.KeepTextInput
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.uiery.kds.KeepTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.uiery.kds.KeepCheckbox
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.appselection.AndroidBlockExemptPackageProvider
import com.uiery.keep.appselection.InstalledAppRepository
import com.uiery.keep.model.AppInfo
import com.uiery.keep.domain.websiteblocking.DomainNameNormalizationResult
import com.uiery.keep.domain.websiteblocking.DomainNamePolicy
import com.uiery.keep.domain.websiteblocking.WebsiteLockPresetCatalog
import com.uiery.keep.domain.websiteblocking.WebsiteLockPresetSelectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CategoryBottomSheetContent(
    modifier: Modifier = Modifier,
    storeSelectApps: Set<String>,
    onComplete: (Set<String>) -> Unit,
    selectionMode: AppSelectionMode = AppSelectionMode.Multiple,
    onSingleComplete: ((packageName: String, appLabel: String) -> Unit)? = null,
    websiteSelectionEnabled: Boolean = false,
    storeSelectedWebDomains: Set<String> = emptySet(),
    onCompleteTargets: ((selectedApps: Set<String>, selectedWebDomains: Set<String>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val installedAppRepository = remember(context) {
        InstalledAppRepository(
            packageManager = context.packageManager,
            blockExemptPackageProvider = AndroidBlockExemptPackageProvider(context.applicationContext),
        )
    }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(installedAppRepository) {
        val loadedApps = withContext(Dispatchers.IO) {
            installedAppRepository.loadSelectableApps()
        }
        apps = loadedApps
        isLoading = false
    }

    CategoryBottomSheetLoadedContent(
        modifier = modifier,
        apps = apps,
        storeSelectApps = storeSelectApps,
        isLoading = isLoading,
        onComplete = onComplete,
        selectionMode = selectionMode,
        onSingleComplete = onSingleComplete,
        websiteSelectionEnabled = websiteSelectionEnabled,
        storeSelectedWebDomains = storeSelectedWebDomains,
        onCompleteTargets = onCompleteTargets,
    )
}

@Composable
fun CategoryBottomSheetLoadedContent(
    modifier: Modifier = Modifier,
    apps: List<AppInfo>,
    storeSelectApps: Set<String>,
    isLoading: Boolean = false,
    onComplete: (Set<String>) -> Unit,
    selectionMode: AppSelectionMode = AppSelectionMode.Multiple,
    onSingleComplete: ((packageName: String, appLabel: String) -> Unit)? = null,
    websiteSelectionEnabled: Boolean = false,
    storeSelectedWebDomains: Set<String> = emptySet(),
    onCompleteTargets: ((selectedApps: Set<String>, selectedWebDomains: Set<String>) -> Unit)? = null,
) {
    val initialSelectedAppPackages = remember(apps, selectionMode) {
        when (selectionMode) {
            AppSelectionMode.Multiple -> storeSelectApps.toSet()
            AppSelectionMode.Single -> apps
                .firstOrNull { it.packageName in storeSelectApps }
                ?.packageName
                ?.let(::setOf)
                .orEmpty()
        }
    }
    var selectedAppPackages by remember(apps, selectionMode) { mutableStateOf(initialSelectedAppPackages) }
    // 담긴 순서를 지키는 목록이다. 정렬해 두면 방금 담은 도메인이 어디로 갔는지 알 수 없어
    // 추가가 반영됐는지 확인할 방법이 사라진다. 회전이나 프로세스 재생성으로 편집 중이던
    // 선택이 통째로 날아가지 않도록 저장한다.
    var selectedWebDomains by rememberSaveable(
        storeSelectedWebDomains,
        stateSaver = listSaver<List<String>, String>(save = { it }, restore = { it }),
    ) { mutableStateOf(storeSelectedWebDomains.sorted()) }
    var selectedTargetIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTargetType = LockTargetType.entries[selectedTargetIndex]
    val allAppPackages = remember(apps) { apps.map { it.packageName } }
    val orderedApps = remember(apps) {
        val appsByPackage = apps.associateBy { it.packageName }
        orderSelectableAppPackagesByInitialSelection(
            appPackages = apps.map { it.packageName },
            initiallySelectedPackages = initialSelectedAppPackages,
        ).mapNotNull { appsByPackage[it] }
    }
    val isSelectAll = areAllSelectableAppsSelected(selectedAppPackages, allAppPackages)
    val selectAllStateDescription = stringResource(
        id = if (isSelectAll) R.string.cd_tab_selected else R.string.cd_tab_not_selected,
    )
    var searchContent by remember { mutableStateOf("") }
    val sheetDragGuard = rememberSheetDragGuard()

    // 키보드가 올라오면 시트가 그만큼 짧아진다. 목록은 남은 높이를 쓰는 유일한 요소라
    // 모든 축소를 혼자 떠안고 0이 된다. 입력하는 동안 무엇이 담겼는지 볼 수 없으면
    // 입력 자체가 불안해지므로, 그때는 큰 제목을 접어 목록에 높이를 돌려준다.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        if (!imeVisible) {
            Spacer(modifier = Modifier.padding(top = 40.dp))
            Text(
                text = stringResource(
                    if (websiteSelectionEnabled) R.string.lock_target_selection else R.string.activity_selection,
                ),
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = KeepTheme.colors.onSurfaceVariant,
            )
        } else {
            Spacer(modifier = Modifier.padding(top = 20.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (websiteSelectionEnabled) {
            KeepSegmentedControl(
                modifier = Modifier.fillMaxWidth(),
                items = LockTargetType.entries.map { targetType ->
                    stringResource(
                        if (targetType == LockTargetType.Apps) {
                            R.string.lock_target_apps
                        } else {
                            R.string.lock_target_websites
                        },
                    )
                },
                selectedIndex = selectedTargetIndex,
                onItemSelected = { index -> selectedTargetIndex = index },
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (selectedTargetType == LockTargetType.Apps) {
            SearchTextField(
                value = { searchContent },
                hint = stringResource(R.string.search),
                onValueChange = { searchContent = it },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (selectedTargetType == LockTargetType.Websites) {
            WebsiteLockListEditor(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                selectedDomains = selectedWebDomains,
                onSelectedDomainsChange = { selectedWebDomains = it },
                compact = imeVisible,
            )
        } else if (isLoading) {
            // 형제 가지들과 달리 weight 없이 fillMaxSize 였다. Column 에서 그러면 남은
            // 높이를 전부 삼켜, 아래에 있는 요약 줄과 완료 버튼을 화면 밖으로 밀어냈다.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                KeepCircularProgressIndicator(
                    color = KeepTheme.colors.primary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("category_app_list")
                    .nestedScroll(sheetDragGuard)
                    .background(
                        shape = RoundedCornerShape(12.dp),
                        color = KeepTheme.semanticColors.background.neutralWeak,
                    ),
            ) {
                if (selectionMode.showsSelectAll && searchContent.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = isSelectAll,
                                    role = Role.Checkbox,
                                    onValueChange = { checked ->
                                        selectedAppPackages = toggleAllSelectableAppsSelection(
                                            currentSelection = selectedAppPackages,
                                            allAppPackages = allAppPackages,
                                            checked = checked,
                                        )
                                    },
                                )
                                .padding(vertical = 10.dp)
                                .semantics { stateDescription = selectAllStateDescription }
                                .testTag("category_select_all_row"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KeepCheckbox(
                                checked = isSelectAll,
                                modifier = Modifier.testTag("category_select_all_checkbox"),
                                onCheckedChange = null,
                            )
                            Image(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                painter = painterResource(R.drawable.kepp_icon),
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(R.string.all_apps),
                                color = KeepTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(
                    items = orderedApps
                        .filter { it.appName.contains(searchContent, ignoreCase = true) },
                    key = { it.packageName }
                ) { app ->
                    val checked = selectedAppPackages.contains(app.packageName)
                    when (selectionMode) {
                        AppSelectionMode.Multiple -> AppItem(
                            modifier = Modifier.testTag("category_app_row_${app.packageName}"),
                            checkboxModifier = Modifier.testTag("category_app_checkbox_${app.packageName}"),
                            image = app.appIcon.toBitmap().asImageBitmap(),
                            name = app.appName,
                            checked = isSelectAll || checked,
                            onCheckedChange = {
                                selectedAppPackages = updateSelectableAppSelection(
                                    mode = selectionMode,
                                    currentSelection = selectedAppPackages,
                                    packageName = app.packageName,
                                )
                            },
                        )

                        AppSelectionMode.Single -> SingleSelectionAppItem(
                            modifier = Modifier.testTag("category_app_row_${app.packageName}"),
                            name = app.appName,
                            image = app.appIcon.toBitmap().asImageBitmap(),
                            selected = checked,
                            onClick = {
                                selectedAppPackages = updateSelectableAppSelection(
                                    mode = selectionMode,
                                    currentSelection = selectedAppPackages,
                                    packageName = app.packageName,
                                )
                            },
                        )
                    }
                }
            }
        }
        // 탭을 넘어가면 반대편 탭의 선택은 화면에서 사라진다. 무엇을 저장하는지는
        // 두 탭 어디에서나 보여야 한다.
        if (websiteSelectionEnabled) {
            Text(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .testTag("lock_targets_summary"),
                text = stringResource(
                    R.string.lock_targets_selected,
                    selectedAppPackages.size,
                    selectedWebDomains.size,
                ),
                color = KeepTheme.colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        KeepButton(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("category_selection_complete")
                .padding(top = 18.dp),
            text = stringResource(R.string.selection_complete),
            onClick = {
                val selectedApp = if (selectionMode == AppSelectionMode.Single) {
                    apps.firstOrNull { it.packageName in selectedAppPackages }
                } else {
                    null
                }
                if (selectedApp != null && onSingleComplete != null) {
                    onSingleComplete(selectedApp.packageName, selectedApp.appName)
                } else if (websiteSelectionEnabled && onCompleteTargets != null) {
                    onCompleteTargets(selectedAppPackages, selectedWebDomains.toSet())
                } else {
                    onComplete(selectedAppPackages)
                }
            },
        )
    }
}

private enum class LockTargetType {
    Apps,
    Websites,
}

/**
 * 리스트가 다 쓰지 못한 스크롤과 속도를 시트에게 넘기지 않는다.
 *
 * 이 시트의 목록은 항상 맨 위에서 시작하므로, 그대로 두면 처음 아래로 쓸어내리는 동작이
 * 언제나 시트를 끌어내린다. 목록을 읽으려던 손짓이 닫으려는 손짓으로 읽히는 셈이다.
 * 드래그 핸들과 제목 영역은 이 목록 바깥이라 거기서 끌어 닫는 길은 그대로 남는다.
 */
@Composable
private fun rememberSheetDragGuard(): NestedScrollConnection = remember {
    object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset = available

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
            available
    }
}

@Composable
private fun WebsiteLockListEditor(
    selectedDomains: List<String>,
    onSelectedDomainsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var validationError by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val invalidMessage = stringResource(R.string.website_domain_invalid)

    // 방금 담은 것을 보여주는 일은 담는 경로마다 따로 붙일 수 없다. 버튼과 키보드 완료가
    // 갈라지면 한쪽 경로에서만 확인이 되는 상태가 생긴다.
    val showTopOfList = { coroutineScope.launch { listState.animateScrollToItem(0) } }
    val addTypedDomain = {
        when (val result = DomainNamePolicy.normalize(input)) {
            is DomainNameNormalizationResult.Valid -> {
                val domain = result.domain.value
                // 이미 담긴 도메인을 다시 입력해도 맨 앞으로 올린다. 아무 일도 일어나지
                // 않으면 입력이 삼켜진 것과 구분되지 않는다.
                onSelectedDomainsChange(listOf(domain) + selectedDomains.filterNot { it == domain })
                input = ""
                validationError = false
                showTopOfList()
            }
            is DomainNameNormalizationResult.Invalid -> validationError = true
        }
        Unit
    }

    Column(modifier = modifier) {
        if (!compact) {
            Text(
                text = stringResource(R.string.website_lock_description),
                color = KeepTheme.colors.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        // 라벨과 오류가 입력창에 붙어 있어야 무엇을 넣는 칸인지, 무엇이 틀렸는지가
        // 입력하는 자리에서 읽힌다.
        KeepField(
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.website_domain_label),
            errorMessage = invalidMessage.takeIf { validationError },
        ) { fieldHasError ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KeepTextInput(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("website_domain_input"),
                    value = input,
                    onValueChange = {
                        input = it
                        validationError = false
                    },
                    placeholder = { Text(text = stringResource(R.string.website_domain_hint)) },
                    isError = fieldHasError,
                    errorMessage = invalidMessage.takeIf { validationError },
                    singleLine = true,
                    // 도메인은 문장이 아니다. 자동 대문자·자동수정이 끼면 입력한 것과
                    // 다른 값이 담긴다. 완료 키로도 담을 수 있어야 한 개 넣을 때마다
                    // 키보드와 버튼을 왕복하지 않는다.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { addTypedDomain() }),
                )
                KeepButton(
                    modifier = Modifier.testTag("website_domain_add"),
                    text = stringResource(R.string.add),
                    size = KeepButtonSize.Large,
                    bottomSpacing = false,
                    onClick = addTypedDomain,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("website_lock_list")
                .nestedScroll(rememberSheetDragGuard())
                .background(
                    shape = RoundedCornerShape(12.dp),
                    color = KeepTheme.semanticColors.background.neutralWeak,
                ),
        ) {
            // 선택한 목록이 먼저다. 추가·삭제의 결과가 입력창 바로 아래에서 보여야
            // 방금 한 조작이 반영됐는지 알 수 있다.
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    text = stringResource(R.string.website_lock_selected),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (selectedDomains.isEmpty()) {
                item {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        text = stringResource(R.string.website_lock_empty),
                        color = KeepTheme.colors.onSurface,
                    )
                }
            } else {
                items(selectedDomains, key = { it }) { domain ->
                    val deleteLabel = stringResource(R.string.website_lock_delete_domain, domain)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = domain,
                            color = KeepTheme.colors.onSurfaceVariant,
                        )
                        // 행마다 "삭제"만 읽히면 어느 도메인을 지우는 버튼인지 알 수 없다.
                        KeepTextButton(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = deleteLabel },
                            onClick = { onSelectedDomainsChange(selectedDomains - domain) },
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
            item {
                Text(
                    modifier = Modifier.padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 18.dp,
                        bottom = 10.dp,
                    ),
                    text = stringResource(R.string.website_lock_recommended),
                    color = KeepTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(
                items = WebsiteLockPresetCatalog.popular,
                key = { preset -> "preset_${preset.id}" },
            ) { preset ->
                // 프리셋은 담기만 한다. 빼는 일은 위 선택 목록이 소유한다.
                val added = WebsiteLockPresetSelectionPolicy.isSelected(
                    selectedDomains = selectedDomains.toSet(),
                    preset = preset,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (added) {
                                // 담긴 행은 누를 수 없어 시맨틱이 저절로 묶이지 않는다.
                                // 묶어 주지 않으면 한 줄짜리 항목을 세 번 끊어 읽는다.
                                Modifier.semantics(mergeDescendants = true) { }
                            } else {
                                Modifier.clickable(role = Role.Button) {
                                    onSelectedDomainsChange(
                                        WebsiteLockPresetSelectionPolicy.add(
                                            selectedDomains = selectedDomains,
                                            preset = preset,
                                        ),
                                    )
                                    showTopOfList()
                                }
                            },
                        )
                        .testTag("website_preset_${preset.id}")
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.serviceName,
                            color = KeepTheme.colors.onSurfaceVariant,
                        )
                        Text(
                            text = preset.domains.joinToString { it.value },
                            color = KeepTheme.colors.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = stringResource(
                            if (added) R.string.website_lock_preset_added else R.string.add,
                        ),
                        color = if (added) {
                            KeepTheme.semanticColors.foreground.muted
                        } else {
                            KeepTheme.semanticColors.foreground.brand
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
            // 차단이 완전하지 않을 수 있다는 사실은 목록 맨 위를 차지할 만큼 자주 읽을
            // 내용은 아니지만, 목록을 끝까지 본 사람은 반드시 만나야 한다.
            item {
                Text(
                    modifier = Modifier.padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 18.dp,
                        bottom = 14.dp,
                    ),
                    text = stringResource(R.string.website_lock_dns_caveat),
                    color = KeepTheme.semanticColors.foreground.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

enum class AppSelectionMode(internal val showsSelectAll: Boolean) {
    Multiple(showsSelectAll = true),
    Single(showsSelectAll = false),
}

@Composable
private fun SingleSelectionAppItem(
    name: String,
    image: ImageBitmap,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectionStateDescription = stringResource(
        id = if (selected) R.string.cd_tab_selected else R.string.cd_tab_not_selected,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 10.dp)
            .semantics { stateDescription = selectionStateDescription }
            .then(modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeepRadioButton(selected = selected, onClick = null)
        Image(
            modifier = Modifier.size(30.dp),
            bitmap = image,
            contentDescription = null,
        )
        Text(text = name, color = KeepTheme.colors.onSurfaceVariant)
    }
}

internal fun toggleAllSelectableAppsSelection(
    currentSelection: Set<String>,
    allAppPackages: Collection<String>,
    checked: Boolean,
): Set<String> = if (checked) {
    allAppPackages.toSet()
} else {
    emptySet()
}

internal fun toggleSelectableAppSelection(
    currentSelection: Set<String>,
    packageName: String,
): Set<String> = if (packageName in currentSelection) {
    currentSelection - packageName
} else {
    currentSelection + packageName
}

internal fun updateSelectableAppSelection(
    mode: AppSelectionMode,
    currentSelection: Set<String>,
    packageName: String,
): Set<String> = when (mode) {
    AppSelectionMode.Multiple -> toggleSelectableAppSelection(currentSelection, packageName)
    AppSelectionMode.Single -> setOf(packageName)
}

internal fun areAllSelectableAppsSelected(
    currentSelection: Set<String>,
    allAppPackages: Collection<String>,
): Boolean {
    val loadedPackages = allAppPackages.toSet()
    return loadedPackages.isNotEmpty() && currentSelection.containsAll(loadedPackages)
}

internal fun orderSelectableAppPackagesByInitialSelection(
    appPackages: List<String>,
    initiallySelectedPackages: Set<String>,
): List<String> = appPackages
    .withIndex()
    .sortedWith(
        compareByDescending<IndexedValue<String>> { it.value in initiallySelectedPackages }
            .thenBy { it.index },
    )
    .map { it.value }

@Preview
@Composable
private fun CategoryBottomSheetContentPreview() {
    CategoryBottomSheetContent(
        storeSelectApps = emptySet(),
        onComplete = { },
    )
}
