package dev.dylanakp.abysstvlauncher.navigation

import kotlinx.serialization.Serializable

sealed interface AbyssRoute {
    @Serializable
    data object Home : AbyssRoute

    @Serializable
    data object Movies : AbyssRoute

    @Serializable
    data object Series : AbyssRoute

    @Serializable
    data object Apps : AbyssRoute

    @Serializable
    data object Discover : AbyssRoute

    @Serializable
    data class MovieDetail(val movieId: String) : AbyssRoute

    @Serializable
    data class SeriesDetail(val seriesId: String) : AbyssRoute

    @Serializable
    data class Player(
        val itemId: String,
        val streamUrl: String,
        val title: String,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null,
        val resumePositionMs: Long = 0L
    ) : AbyssRoute

    @Serializable
    data object Settings : AbyssRoute
}
