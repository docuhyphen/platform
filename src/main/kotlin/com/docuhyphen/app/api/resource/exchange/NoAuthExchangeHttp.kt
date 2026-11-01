package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.NoAuthOtpException
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.exchange.NoAuthExchangeAccessExpiredException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*

object NoAuthExchangeHttp
{
    const val ACCESS_TOKEN_HEADER = "x-no-auth-access-token"
    const val SHARE_LINK_TOKEN_HEADER = "X-Share-Link-Token"
    private const val ACCESS_VERIFICATION_REQUIRED_REASON = "NO_AUTH_ACCESS_EXPIRED"
    private const val OTP_RATE_LIMITED_REASON = "OTP_RATE_LIMITED"
    private const val OTP_LOCKED_REASON = "OTP_LOCKED"
    private const val RETRY_AFTER_HEADER = "Retry-After"

    fun accessVerificationRequired(exception: NoAuthExchangeAccessExpiredException): Response =
        Response.status(FORBIDDEN)
            .entity(
                ResponseError(
                    errorMessage = exception.message,
                    reasonCode = ACCESS_VERIFICATION_REQUIRED_REASON,
                )
            )
            .build()

    fun otpRefused(exception: NoAuthOtpException): Response
    {
        val status =
            if (exception.reasonCode == OTP_RATE_LIMITED_REASON || exception.reasonCode == OTP_LOCKED_REASON)
                TOO_MANY_REQUESTS
            else
                BAD_REQUEST
        val response = Response.status(status)
            .entity(
                ResponseError(
                    errorMessage = exception.message,
                    reasonCode = exception.reasonCode,
                    retryAfterSeconds = exception.retryAfterSeconds,
                )
            )
        exception.retryAfterSeconds?.let { response.header(RETRY_AFTER_HEADER, it) }
        return response.build()
    }
}
