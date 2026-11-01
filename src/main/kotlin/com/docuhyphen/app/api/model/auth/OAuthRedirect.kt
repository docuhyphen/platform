package com.docuhyphen.app.api.model.auth

import jakarta.ws.rs.core.NewCookie
import java.net.URI

data class OAuthRedirect(
    val location: URI,
    val cookies: List<NewCookie> = emptyList(),
)
