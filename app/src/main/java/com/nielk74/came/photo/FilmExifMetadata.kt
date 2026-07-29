package com.nielk74.came.photo

private const val FILM_ID_PREFIX = "came:film-id="
private const val FILM_DESCRIPTION_PREFIX = "Film filter: "

/** Stable machine-readable value stored in EXIF UserComment. */
internal fun filmFilterUserComment(profileId: String): String {
    require(profileId.isNotBlank())
    return FILM_ID_PREFIX + profileId.trim()
}

/** Human-readable value stored in the standard EXIF ImageDescription field. */
internal fun filmFilterDescription(displayName: String): String {
    require(displayName.isNotBlank())
    return FILM_DESCRIPTION_PREFIX + displayName.trim()
}

internal fun filmFilterIdFromUserComment(comment: String?): String? =
    comment
        ?.trim()
        ?.takeIf { it.startsWith(FILM_ID_PREFIX) }
        ?.removePrefix(FILM_ID_PREFIX)
        ?.trim()
        ?.takeIf(String::isNotBlank)

internal fun filmFilterNameFromDescription(description: String?): String? =
    description
        ?.trim()
        ?.takeIf { it.startsWith(FILM_DESCRIPTION_PREFIX) }
        ?.removePrefix(FILM_DESCRIPTION_PREFIX)
        ?.trim()
        ?.takeIf(String::isNotBlank)

internal fun resolvedFilmFilterName(
    description: String?,
    userComment: String?,
    profileNameForId: (String) -> String?,
): String? =
    filmFilterNameFromDescription(description)
        ?: filmFilterIdFromUserComment(userComment)?.let(profileNameForId)
