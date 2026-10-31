package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.service.auth.TokenIssuanceService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.arc.Arc
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import java.util.*

internal class InformationRequestSignedInCaller private constructor(val userId: UUID, private val accessToken: String) {
    fun authorized(): RequestSpecification = given().header("Authorization", "Bearer $accessToken")

    fun command(idempotencyKey: String): RequestSpecification =
        authorized()
            .header("Idempotency-Key", idempotencyKey)
            .contentType(ContentType.JSON)

    companion object {
        fun signIn(appUsers: AppUserService, userId: UUID): InformationRequestSignedInCaller {
            val context = Arc.container().requestContext()
            context.activate()
            val accessToken = try {
                val user = requireNotNull(appUsers.getById(userId))
                Arc.container().instance(TokenIssuanceService::class.java).get().issueTokenTriple(user).accessToken
            } finally {
                context.terminate()
            }
            return InformationRequestSignedInCaller(userId, accessToken)
        }
    }
}
