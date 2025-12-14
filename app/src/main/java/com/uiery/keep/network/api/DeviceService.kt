package com.uiery.keep.network.api

import com.uiery.keep.network.device.RegisterDeviceRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface DeviceService {
    @POST("/devices")
    suspend fun registerDevice(
        @Body registerDeviceRequest: RegisterDeviceRequest,
    )
}