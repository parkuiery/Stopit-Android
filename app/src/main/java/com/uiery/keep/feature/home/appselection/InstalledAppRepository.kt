package com.uiery.keep.feature.home.appselection

import android.content.pm.PackageManager
import com.uiery.keep.BuildConfig
import com.uiery.keep.model.AppInfo

class InstalledAppRepository(
    private val packageManager: PackageManager,
    private val ownPackageName: String = BuildConfig.APPLICATION_ID,
) {
    /**
     * Loads the launchable apps a user can select as blocking targets.
     *
     * QUERY_ALL_PACKAGES is intentionally consumed behind this repository so the Compose
     * picker does not own Play policy-sensitive package visibility details.
     */
    fun loadSelectableApps(): List<AppInfo> {
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val candidatesByPackage = installedApps.associate { app ->
            app.packageName to SelectableAppCandidate(
                packageName = app.packageName,
                appName = packageManager.getApplicationLabel(app).toString(),
                hasLaunchIntent = packageManager.getLaunchIntentForPackage(app.packageName) != null,
            )
        }
        val installedAppsByPackage = installedApps.associateBy { it.packageName }

        return SelectableAppPolicy
            .filterSelectableApps(
                candidates = candidatesByPackage.values.toList(),
                ownPackageName = ownPackageName,
            )
            .mapNotNull { candidate ->
                val app = installedAppsByPackage[candidate.packageName] ?: return@mapNotNull null
                AppInfo(
                    packageName = candidate.packageName,
                    appName = candidate.appName,
                    appIcon = packageManager.getApplicationIcon(app),
                    isChecked = false,
                )
            }
    }
}
