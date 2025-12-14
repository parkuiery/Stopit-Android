package com.uiery.keep.network.api

import com.uiery.keep.network.routine.CreateRoutineRequest
import com.uiery.keep.network.routine.CreateRoutineResponse
import com.uiery.keep.network.routine.GetDetailRoutineResponse
import com.uiery.keep.network.routine.PostReceivedRoutineLogRequest
import com.uiery.keep.network.routine.TurnRoutineRequest
import com.uiery.keep.network.routine.UpdateRoutineRequest
import com.uiery.keep.network.routine.UpdateRoutineResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface RoutineService {

    @POST("/routines")
    suspend fun createRoutine(
        @Body createRoutineRequest: CreateRoutineRequest,
    ): CreateRoutineResponse

    @GET("/routines")
    suspend fun getAllRoutines(
        @Query("deviceId") deviceId: String,
    ): List<GetDetailRoutineResponse>

    @DELETE("/routines/{id}")
    suspend fun deleteRoutine(
        @Path("id") id: String,
    )

    @PUT("/routines/{id}")
    suspend fun updateRoutine(
        @Path("id") id: String,
        @Body updateRoutineRequest: UpdateRoutineRequest,
    ): UpdateRoutineResponse

    @GET("/routines/{id}")
    suspend fun getDetailRoutine(
        @Path("id") id: String,
    ): GetDetailRoutineResponse

    @POST("/routine-logs/received")
    suspend fun postReceivedRoutineLog(
        @Body postReceivedRoutineLogRequest: PostReceivedRoutineLogRequest,
    )

    @PUT("/routines/{id}/enabled")
    suspend fun turnRoutine(
        @Path("id") id: String,
        @Body turnRoutineRequest: TurnRoutineRequest,
    ): GetDetailRoutineResponse
}