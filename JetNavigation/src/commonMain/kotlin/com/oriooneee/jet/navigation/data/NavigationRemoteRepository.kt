package com.oriooneee.jet.navigation.data

import com.oriooneee.jet.navigation.domain.entities.graph.MasterNavigation
import com.oriooneee.jet.navigation.domain.entities.weather.WeatherResponse

interface NavigationRemoteRepository{
   suspend fun getMainNavigation(): Result<MasterNavigation>
    suspend fun getWeather(): Result<WeatherResponse>
}