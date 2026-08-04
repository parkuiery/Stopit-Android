package com.uiery.keep.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import com.uiery.kds.KeepButton
import com.uiery.kds.KeepButtonSize
import com.uiery.kds.KeepCircularProgressIndicator
import com.uiery.kds.KeepRadioButton
import com.uiery.kds.KeepSegmentedControl
import androidx.compose.material3.Text
import com.uiery.kds.KeepTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.uiery.kds.KeepButton
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
    var selectedWebDomains by remember(storeSelectedWebDomains) {
        mutableStateOf(storeSelectedWebDomains.toSet())
    }
    var selectedTargetType by remember { mutableStateOf(LockTargetType.Apps) }
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
                selectedIndex = LockTargetType.entries.indexOf(selectedTargetType),
                onItemSelected = { index ->
                    selectedTargetType = LockTargetType.entries[index]
                },
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
            Box(
                modifier = Modifier.fillMaxSize(),
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
                    onCompleteTargets(selectedAppPackages, selectedWebDomains)
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

@Composable
private fun WebsiteLockListEditor(
    selectedDomains: Set<String>,
    onSelectedDomainsChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var input by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier) {
        if (!compact) {
            Text(
                text = stringResource(R.string.website_lock_description),
                color = KeepTheme.colors.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchTextField(
                modifier = Modifier.weight(1f),
                value = { input },
                hint = stringResource(R.string.website_domain_hint),
                onValueChange = {
                    input = it
                    validationError = false
                },
            )
            KeepButton(
                text = stringResource(R.string.add),
                size = KeepButtonSize.Small,
                bottomSpacing = false,
                onClick = {
                    when (val result = DomainNamePolicy.normalize(input)) {
                        is DomainNameNormalizationResult.Valid -> {
                            onSelectedDomainsChange(selectedDomains + result.domain.value)
                            input = ""
                            validationError = false
                            // 방금 담은 도메인은 목록 맨 위에 있다. 스크롤이 아래에 머물러
                            // 있으면 추가된 것을 확인할 방법이 없다.
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        }
                        is DomainNameNormalizationResult.Invalid -> validationError = true
                    }
                },
            )
        }
        if (validationError) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = stringResource(R.string.website_domain_invalid),
                color = KeepTheme.colors.error,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    shape = RoundedCornerShape(12.dp),
                    color = KeepTheme.colors.secondary,
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
                items(selectedDomains.sorted(), key = { it }) { domain ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = domain,
                            color = KeepTheme.colors.onSurfaceVariant,
                        )
                        KeepTextButton(
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
                val presetDomains = preset.domains.map { it.value }.toSet()
                val checked = WebsiteLockPresetSelectionPolicy.isSelected(
                    selectedDomains = selectedDomains,
                    preset = preset,
                )
                val selectionStateDescription = stringResource(
                    id = if (checked) R.string.cd_tab_selected else R.string.cd_tab_not_selected,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { selected ->
                                onSelectedDomainsChange(
                                    WebsiteLockPresetSelectionPolicy.updateSelection(
                                        selectedDomains = selectedDomains,
                                        preset = preset,
                                        selected = selected,
                                    ),
                                )
                            },
                        )
                        .semantics { stateDescription = selectionStateDescription }
                        .testTag("website_preset_${preset.id}")
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KeepCheckbox(
                        checked = checked,
                        onCheckedChange = null,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.serviceName,
                            color = KeepTheme.colors.onSurfaceVariant,
                        )
                        Text(
                            text = presetDomains.sorted().joinToString(),
                            color = KeepTheme.colors.onSurface,
                            fontSize = 12.sp,
                        )
                    }
                }
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
