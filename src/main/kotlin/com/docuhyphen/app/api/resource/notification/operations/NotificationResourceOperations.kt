package com.docuhyphen.app.api.resource.notification.operations

import com.docuhyphen.app.api.model.dto.NotificationReadReceiptRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface NotificationResourceOperations {
    @GET
    fun listNotifications(
        @QueryParam("limit") requestedLimit: Int?,
        @QueryParam("beforeTimestamp") beforeTimestamp: Long?,
        @QueryParam("beforeId") beforeId: String?,
    ): Response

    @POST
    @Path("/read-receipts")
    fun createReadReceipts(request: NotificationReadReceiptRequest): Response
}
