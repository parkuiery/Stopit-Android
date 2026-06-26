package com.uiery.keep.service

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.uiery.keep.DeviceTokenManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class KeepMessagingService : FirebaseMessagingService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DeviceTokenManagerEntryPoint {
        fun deviceTokenManager(): DeviceTokenManager
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenPersistenceRunner.launchDeviceTokenPersistence(token) { newToken ->
            persistNewTokenForContext(applicationContext, newToken)
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
