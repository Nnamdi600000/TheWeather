package com.codennamdi.theweather.network

import com.codennamdi.theweather.models.WeatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {
    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("unit") unit: String?,
        @Query("appId") appId: String?
    ): Call<WeatherResponse>
}