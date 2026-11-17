package com.example.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface SimRoute {
    @Serializable
    data object Home : SimRoute

    @Serializable
    data object Camera : SimRoute

    @Serializable
    data class Gallery(val initialFilter: String = "ALL") : SimRoute

    @Serializable
    data class ImageEditor(val uriString: String) : SimRoute

    @Serializable
    data object Music : SimRoute

    @Serializable
    data object Files : SimRoute

    @Serializable
    data class WebApp(
        val appId: String,
        val title: String,
        val initialUrl: String
    ) : SimRoute

    @Serializable
    data object DeviceSettings : SimRoute
}
