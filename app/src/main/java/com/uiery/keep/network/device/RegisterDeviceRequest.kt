package com.uiery.keep.network.device

data class RegisterDeviceRequest(
    val deviceId: String,
    val fcmToken: String,
    val timeZone: String,
    val platform: String,
)