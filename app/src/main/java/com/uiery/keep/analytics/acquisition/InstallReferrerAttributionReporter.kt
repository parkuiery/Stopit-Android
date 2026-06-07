package com.uiery.keep.analytics.acquisition

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.uiery.keep.KeepDataSource
import com.uiery.keep.analytics.KeepAnalytics
import com.uiery.keep.datastore.PreferencesKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

interface InstallReferrerLookup {
    suspend fun lookup(): InstallReferrerLookupResult
}

interface InstallReferrerAttributionStore {
    suspend fun hasCheckedInstallReferrerAttribution(): Boolean
    suspend fun markInstallReferrerAttributionChecked()
}

data class InstallReferrerLookupResult(
    val rawReferrer: String?,
    val status: InstallReferrerLookupStatus,
    val latencyMillis: Long?,
)

@Singleton
class InstallReferrerAttributionReporter @Inject constructor(
    private val lookup: InstallReferrerLookup,
    private val store: InstallReferrerAttributionStore,
    private val analytics: KeepAnalytics,
) {
    suspend fun checkOnceAfterFirstLaunch() {
        if (store.hasCheckedInstallReferrerAttribution()) return
        val result = runCatching { lookup.lookup() }.getOrElse {
            InstallReferrerLookupResult(
                rawReferrer = null,
                status = InstallReferrerLookupStatus.ERROR,
                latencyMillis = null,
            )
        }
        val attribution = AcquisitionAttributionParser.parse(
            rawReferrer = result.rawReferrer,
            lookupStatus = result.status,
            latencyMillis = result.latencyMillis,
        )
        analytics.trackInstallReferrerAttributionChecked(attribution)
        store.markInstallReferrerAttributionChecked()
    }
}

@Singleton
class DataStoreInstallReferrerAttributionStore @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
) : InstallReferrerAttributionStore {
    override suspend fun hasCheckedInstallReferrerAttribution(): Boolean =
        dataStore.data.first()[PreferencesKey.HAS_CHECKED_INSTALL_REFERRER_ATTRIBUTION] == true

    override suspend fun markInstallReferrerAttributionChecked() {
        dataStore.edit { preferences ->
            preferences[PreferencesKey.HAS_CHECKED_INSTALL_REFERRER_ATTRIBUTION] = true
        }
    }
}

@Singleton
class PlayInstallReferrerLookup @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstallReferrerLookup {
    override suspend fun lookup(): InstallReferrerLookupResult {
        val startedAt = System.currentTimeMillis()
        val client = InstallReferrerClient.newBuilder(context).build()
        val details = connectAndRead(client) ?: return InstallReferrerLookupResult(
            rawReferrer = null,
            status = InstallReferrerLookupStatus.UNAVAILABLE,
            latencyMillis = System.currentTimeMillis() - startedAt,
        )
        return InstallReferrerLookupResult(
            rawReferrer = details.installReferrer,
            status = InstallReferrerLookupStatus.SUCCESS,
            latencyMillis = System.currentTimeMillis() - startedAt,
        )
    }

    private suspend fun connectAndRead(client: InstallReferrerClient): ReferrerDetails? =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { runCatching { client.endConnection() } }
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    val details = when (responseCode) {
                        InstallReferrerClient.InstallReferrerResponse.OK -> runCatching { client.installReferrer }.getOrNull()
                        else -> null
                    }
                    runCatching { client.endConnection() }
                    if (continuation.isActive) continuation.resume(details)
                }

                override fun onInstallReferrerServiceDisconnected() {
                    runCatching { client.endConnection() }
                    if (continuation.isActive) continuation.resume(null)
                }
            })
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class InstallReferrerAttributionModule {
    @Binds
    abstract fun bindInstallReferrerLookup(impl: PlayInstallReferrerLookup): InstallReferrerLookup

    @Binds
    abstract fun bindInstallReferrerAttributionStore(
        impl: DataStoreInstallReferrerAttributionStore,
    ): InstallReferrerAttributionStore
}
