package com.uiery.keep.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.uiery.kds.KeepCheckbox
import com.uiery.kds.theme.KeepTheme
import com.uiery.keep.R
import com.uiery.keep.feature.home.appselection.InstalledAppRepository
import com.uiery.keep.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CategoryBottomSheetContent(
    modifier: Modifier = Modifier,
    storeSelectApps: Set<String>,
    onComplete: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val installedAppRepository = remember(context.packageManager) {
        InstalledAppRepository(context.packageManager)
    }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val selectedAppPackages by remember { mutableStateOf(storeSelectApps.toMutableSet()) }
    var isSelectAll by remember(storeSelectApps, apps) {
        mutableStateOf(apps.map { it.packageName }.toSet() == storeSelectApps.toSet())
    }
    var searchContent by remember { mutableStateOf("") }

    LaunchedEffect(installedAppRepository) {
        val loadedApps = withContext(Dispatchers.IO) {
            installedAppRepository.loadSelectableApps()
        }
        apps = loadedApps
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.padding(top = 40.dp))
        Text(
            text = stringResource(R.string.activity_selection),
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            color = KeepTheme.colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SearchTextField(
            value = { searchContent },
            hint = stringResource(R.string.search),
            onValueChange = { searchContent = it }
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
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
                        color = KeepTheme.colors.secondary
                    ),
            ) {
                if (searchContent.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                isSelectAll = !isSelectAll
                                selectedAppPackages.apply {
                                    clear()
                                    if (isSelectAll) addAll(apps.map { it.packageName })
                                }
                            },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KeepCheckbox(
                                checked = isSelectAll,
                                onCheckedChange = { checked ->
                                    isSelectAll = checked
                                    selectedAppPackages.apply {
                                        clear()
                                        if (checked) addAll(apps.map { it.packageName })
                                    }
                                },
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
                    items = apps
                        .filter { it.appName.contains(searchContent, ignoreCase = true) }
                        .sortedByDescending { it.packageName in storeSelectApps },
                    key = { it.packageName }
                ) { app ->
                    var isCheck by remember(isSelectAll, selectedAppPackages) {
                        mutableStateOf(
                            isSelectAll || selectedAppPackages.contains(app.packageName)
                        )
                    }
                    AppItem(
                        image = app.appIcon.toBitmap().asImageBitmap(),
                        name = app.appName,
                        checked = isCheck,
                        onCheckedChange = {
                            selectedAppPackages.apply {
                                if (contains(app.packageName)) {
                                    remove(app.packageName)
                                    isCheck = false
                                } else {
                                    add(app.packageName)
                                    isCheck = true
                                }
                            }
                            isSelectAll =
                                apps.map { it.packageName }.toSet() == selectedAppPackages.toSet()
                        }
                    )
                }
            }
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 24.dp),
            onClick = { onComplete(selectedAppPackages) },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KeepTheme.colors.primary,
                contentColor = Color.White,
            ),
            contentPadding = PaddingValues(vertical = 18.dp)
        ) {
            Text(
                text = stringResource(R.string.selection_complete),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }
    }
}

@Preview
@Composable
private fun CategoryBottomSheetContentPreview() {
    CategoryBottomSheetContent(
        storeSelectApps = emptySet(),
        onComplete = { },
    )
}