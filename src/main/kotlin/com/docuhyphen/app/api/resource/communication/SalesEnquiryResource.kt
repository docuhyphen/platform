package com.docuhyphen.app.api.resource.communication

import com.docuhyphen.app.api.exception.SalesEnquiryRateLimitedException
import com.docuhyphen.app.api.exception.SalesEnquiryRecipientUnavailableException
import com.docuhyphen.app.api.resource.communication.operations.SalesEnquiryResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SalesEnquiryRequest
import com.docuhyphen.app.api.service.communication.SalesEnquiryService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class SalesEnquiryResource @Inject constructor(
    private val salesEnquiryService: SalesEnquiryService,
) : SalesEnquiryResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SalesEnquiryResource::class.java)
    }

    override fun submitSalesEnquiry(request: SalesEnquiryRequest): Response
    {
        return try
        {
            salesEnquiryService.submitEnquiry(request)
            Response.status(NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SalesEnquiryRateLimitedException ->
                {
                    logger.warn("Sales enquiry rate limited: retryAfter={}", exception.retryAfterSeconds)
                    val response = Response.status(TOO_MANY_REQUESTS)
                        .entity(
                            ResponseError(
                                errorMessage = exception.message,
                                reasonCode = "SALES_ENQUIRY_RATE_LIMITED",
                                retryAfterSeconds = exception.retryAfterSeconds,
                            )
                        )
                    exception.retryAfterSeconds?.let { response.header("Retry-After", it) }
                    response.build()
                }

                is SalesEnquiryRecipientUnavailableException ->
                {
                    logger.error("Sales enquiry could not be routed: {}", exception.message)
                    Response.status(SERVICE_UNAVAILABLE)
                        .entity(ResponseError("Sales enquiries are temporarily unavailable. Please try again later."))
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.warn("Sales enquiry rejected as invalid: {}", exception.message)
                    Response.status(BAD_REQUEST)
                        .entity(ResponseError(exception.message))
                        .build()
                }

                else ->
                {
                    logger.error("Error handling sales enquiry", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while submitting the enquiry"))
                        .build()
                }
            }
        }
    }
}
