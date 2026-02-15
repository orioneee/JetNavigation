package com.oriooneee.jet.navigation.data

import com.oriooneee.jet.navigation.buildconfig.BuildConfig
import com.oriooneee.jet.navigation.domain.entities.Coordinates
import com.oriooneee.jet.navigation.domain.entities.graph.MasterNavigation
import com.oriooneee.jet.navigation.domain.entities.weather.WeatherResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal expect val BuildConfig.API_KEY: String

class NavigationRemoteRepositoryImpl(
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                },
                contentType = ContentType.Any
            )
        }
    }

) : NavigationRemoteRepository {
    companion object {
        val VNTU_COORDINATES = Coordinates(
            latitude = 49.2338836,
            longitude = 28.4375
        )
    }

    private var cachedNavigation: MasterNavigation? = null

    override suspend fun getMainNavigation(): Result<MasterNavigation> {
        cachedNavigation?.let {
            return Result.success(it)
        }

        return runCatching {
            val res = client.get {
                url {
                    takeFrom(BuildConfig.BASE_URL)
                    appendPathSegments("api", "navigation", "")
                    parameter("token", BuildConfig.API_KEY)
                }
            }.body<MasterNavigation>()
            res
        }.onSuccess {
            cachedNavigation = it
        }.onFailure {
            it.printStackTrace()
        }
    }

    override suspend fun getWeather(): Result<WeatherResponse> {
        return runCatching {
            client.get {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "api.open-meteo.com"
                    encodedPath = "/v1/forecast"

                    parameters.append("latitude", VNTU_COORDINATES.latitude.toString())
                    parameters.append("longitude", VNTU_COORDINATES.longitude.toString())
                    parameters.append("current_weather", "true")
                    parameters.append("hourly", "temperature_2m,precipitation")
                }
            }.body<WeatherResponse>()
        }.onFailure {
            it.printStackTrace()
        }
    }

}
