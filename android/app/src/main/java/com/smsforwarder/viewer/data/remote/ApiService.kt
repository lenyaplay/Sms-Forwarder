package com.smsforwarder.viewer.data.remote

import com.smsforwarder.viewer.data.remote.dto.CreateBindingRequest
import com.smsforwarder.viewer.data.remote.dto.CreateBindingResponse
import com.smsforwarder.viewer.data.remote.dto.CreateDeviceRequest
import com.smsforwarder.viewer.data.remote.dto.CreateDownloadTokenRequest
import com.smsforwarder.viewer.data.remote.dto.DeviceCreateResponse
import com.smsforwarder.viewer.data.remote.dto.DeviceListResponse
import com.smsforwarder.viewer.data.remote.dto.DownloadTokenDto
import com.smsforwarder.viewer.data.remote.dto.DownloadTokenListResponse
import com.smsforwarder.viewer.data.remote.dto.LoginRequest
import com.smsforwarder.viewer.data.remote.dto.LogoutRequest
import com.smsforwarder.viewer.data.remote.dto.MessageListResponse
import com.smsforwarder.viewer.data.remote.dto.RefreshRequest
import com.smsforwarder.viewer.data.remote.dto.ReissueUploadTokenRequest
import com.smsforwarder.viewer.data.remote.dto.ReissueUploadTokenResponse
import com.smsforwarder.viewer.data.remote.dto.RevokeDownloadTokenResponse
import com.smsforwarder.viewer.data.remote.dto.TokenPairResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body request: LoginRequest): Response<Unit>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<TokenPairResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<TokenPairResponse>

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>

    @GET("devices")
    suspend fun listDevices(): Response<DeviceListResponse>

    @POST("devices")
    suspend fun createDevice(@Body request: CreateDeviceRequest): Response<DeviceCreateResponse>

    @POST("devices/bindings")
    suspend fun createBinding(@Body request: CreateBindingRequest): Response<CreateBindingResponse>

    @POST("devices/{id}/download_tokens")
    suspend fun createDownloadToken(
        @Path("id") deviceId: Long,
        @Body request: CreateDownloadTokenRequest,
    ): Response<DownloadTokenDto>

    @GET("devices/{id}/download_tokens")
    suspend fun listDownloadTokens(@Path("id") deviceId: Long): Response<DownloadTokenListResponse>

    @DELETE("devices/{id}/download_tokens/{token_id}")
    suspend fun revokeDownloadToken(
        @Path("id") deviceId: Long,
        @Path("token_id") tokenId: Long,
    ): Response<RevokeDownloadTokenResponse>

    @POST("devices/{id}/upload_token")
    suspend fun reissueUploadToken(
        @Path("id") deviceId: Long,
        @Body request: ReissueUploadTokenRequest,
    ): Response<ReissueUploadTokenResponse>

    @GET("devices/{id}/messages")
    suspend fun listMessages(
        @Path("id") deviceId: Long,
        @Query("limit") limit: Int? = null,
        @Query("before_id") beforeId: Long? = null,
        @Query("since") since: String? = null,
        @Query("until") until: String? = null,
    ): Response<MessageListResponse>
}
