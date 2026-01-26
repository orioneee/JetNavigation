package com.oriooneee.jet.navigation.domain.entities.weather


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CurrentWeather(
    @SerialName("interval")
    val interval: Int,
    @SerialName("is_day")
    val isDay: Int,
    @SerialName("temperature")
    val temperature: Double,
    @SerialName("time")
    val time: String,
    @SerialName("weathercode")
    val weatherCode: Int,
    @SerialName("winddirection")
    val windDirection: Int,
    @SerialName("windspeed")
    val windSpeed: Double
)