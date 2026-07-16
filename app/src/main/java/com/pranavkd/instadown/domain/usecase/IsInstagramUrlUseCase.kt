package com.pranavkd.instadown.domain.usecase

import javax.inject.Inject

class IsInstagramUrlUseCase @Inject constructor() {

    private val instagramRegex = Regex(
        "^https?://(www\\.)?(instagram\\.com|instagr\\.am)/.*$",
        RegexOption.IGNORE_CASE
    )

    operator fun invoke(url: String): Boolean {
        return url.isNotBlank() && url.matches(instagramRegex)
    }
}
