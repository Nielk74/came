package com.nielk74.came.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nielk74.came.R
import com.nielk74.came.filters.FilmProfile

/**
 * A real packaging crop for every stock in the catalog.
 *
 * These deliberately live in the UI layer rather than [FilmProfile]: the film response remains
 * portable and testable without Android resources, while the viewfinder and settings share one
 * authoritative art mapping.
 */
@Composable
internal fun FilmPackagingThumbnail(
    profile: FilmProfile,
    modifier: Modifier = Modifier,
) {
    val resource = packagingResource(profile.id)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF2F1EC)),
    ) {
        Image(
            painter = painterResource(resource),
            contentDescription = "${profile.displayName} film packaging",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@DrawableRes
internal fun packagingResource(profileId: String): Int = when (profileId) {
    "portra400" -> R.drawable.film_packaging_portra400
    "portra800" -> R.drawable.film_packaging_portra800
    "gold200" -> R.drawable.film_packaging_gold200
    "ektar100" -> R.drawable.film_packaging_ektar100
    "superia400" -> R.drawable.film_packaging_superia400
    "cinestill800t" -> R.drawable.film_packaging_cinestill800t
    "vision3_250d" -> R.drawable.film_packaging_vision3_250d
    "vision3_500t" -> R.drawable.film_packaging_vision3_500t
    "eterna" -> R.drawable.film_packaging_eterna
    "trix400" -> R.drawable.film_packaging_trix400
    "hp5" -> R.drawable.film_packaging_hp5
    else -> R.drawable.film_packaging_portra400
}
