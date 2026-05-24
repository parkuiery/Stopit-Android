package com.uiery.keep.service

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.uiery.keep.DeviceTokenManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KeepMessagingService : FirebaseMessagingService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DeviceTokenManagerEntryPoint {
        fun deviceTokenManager(): DeviceTokenManager
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            persistNewTokenForContext(applicationContext, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
    }

    companion object {
        suspend fun persistNewTokenForContext(context: Context, token: String) {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                DeviceTokenManagerEntryPoint::class.java
            )
            entryPoint.deviceTokenManager().saveDeviceToken(token)
        }
    }
}
