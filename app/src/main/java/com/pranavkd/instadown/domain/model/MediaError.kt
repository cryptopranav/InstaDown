package com.pranavkd.instadown.domain.model

sealed class MediaError(
    override val message: String? = null,
    cause: Throwable? = null
) : Throwable(message, cause) {
    data class Api(override val message: String?) : MediaError(message)
    data object NoNetwork : MediaError("No internet connection")
    data class InvalidUrl(override val message: String) : MediaError(message)
    data class QuotaExceeded(override val message: String) : MediaError(message)
    data class Unknown(override val message: String) : MediaError(message)
}
