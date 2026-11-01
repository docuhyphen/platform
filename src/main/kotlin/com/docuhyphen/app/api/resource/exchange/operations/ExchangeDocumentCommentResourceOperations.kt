package com.docuhyphen.app.api.resource.exchange.operations

import com.docuhyphen.app.api.resource.model.CommentRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("exchanges/{exchangeId}/documents/{documentId}/comments")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface ExchangeDocumentCommentResourceOperations {
    @POST
    fun addComment(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String,
        commentRequest: CommentRequest,
    ): Response

    @GET
    fun getComments(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response
}
