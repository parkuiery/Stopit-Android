package com.uiery.keep.feature.review

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.uiery.keep.KeepDataSource
import com.uiery.keep.datastore.PreferencesKey
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class AppLifecycleTracker @Inject constructor(
    @KeepDataSource private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStop(owner: LifecycleOwner) {
        scope.launch {
            dataStore.edit { it[PreferencesKey.LAST_BACKGROUNDED_AT_MS] = clock.millis() }
        }
    }
}
