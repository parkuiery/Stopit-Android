package com.uiery.keep.appselection

import android.content.pm.PackageManager
import com.uiery.keep.BuildConfig
import com.uiery.keep.model.AppInfo
import com.uiery.keep.util.AppDisplayMetadataResolver

class InstalledAppRepository(
    private val packageManager: PackageManager,
    private val ownPackageName: String = BuildConfig.APPLICATION_ID,
    private val appDisplayMetadataResolver: AppDisplayMetadataResolver = AppDisplayMetadataResolver(packageManager),
    private val blockExemptPackageProvider: BlockExemptPackageProvider = BlockExemptPackageProvider.None,
) {
    /**
     * Loads the launchable apps a user can select as blocking targets.
     *
     * QUERY_ALL_PACKAGES is intentionally consumed behind this repository so the Compose
     * picker does not own Play policy-sensitive package visibility details.
     *
     * Device essentials such as the home launcher and wallet apps are withheld here so
     * "select all" cannot pull them into the blocked set.
     */
    fun loadSelectableApps(): List<AppInfo> {
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val metadataByPackage = installedApps.associate { app ->
            app.packageName to appDisplayMetadataResolver.resolve(app.packageName)
        }
        val candidatesByPackage = installedApps.associate { app ->
            val metadata = metadataByPackage.getValue(app.packageName)
            app.packageName to SelectableAppCandidate(
                packageName = app.packageName,
                appName = metadata.label,
                hasLaunchIntent = packageManager.getLaunchIntentForPackage(app.packageName) != null,
            )
        }

        return SelectableAppPolicy
            .filterSelectableApps(
                candidates = candidatesByPackage.values.toList(),
                ownPackageName = ownPackageName,
                exemptPackages = blockExemptPackageProvider.exemptPackages().homePackages,
            )
            .map { candidate ->
                val metadata = metadataByPackage.getValue(candidate.packageName)
                AppInfo(
                    packageName = candidate.packageName,
                    appName = metadata.label,
                    appIcon = metadata.icon,
                    isChecked = false,
                )
            }
    }
}
