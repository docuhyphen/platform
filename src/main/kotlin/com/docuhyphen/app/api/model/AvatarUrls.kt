package com.docuhyphen.app.api.model

import java.util.UUID

/**
 * Builds the relative API paths the frontend resolves into authenticated avatar image blobs.
 * A DTO carries one of these paths when the referenced user has a stored profile picture; the
 * frontend fetches the path with its bearer token and turns the response into an object URL.
 */
object AvatarUrls
{
    /** Path that streams a specific user's stored profile picture. */
    fun forUser(userId: UUID): String = "/app-user/$userId/avatar"
}

