package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.resource.model.CommentRequest
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentCommentsService
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("exchanges/{exchangeId}/documents/{documentId}/comments")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class ExchangeDocumentCommentResource @Inject constructor(
    private val documentCommentsService: ExchangeDocumentCommentsService
)
{
    @POST
    @Transactional
    fun addComment(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String,
        commentRequest: CommentRequest,
    ): Response
    {
        val comment = documentCommentsService.addDocumentComment(
            exchangeId,
            documentId,
            commentRequest.commentText,
            commentRequest.isInternal
        )

        return Response.ok(DetailedEntityToDtoTransformer.toDto(comment)).build()
    }

    @GET
    fun getComments(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {

        val comments = documentCommentsService.getDocumentComments(exchangeId, documentId)

        return Response.ok(comments.map { DetailedEntityToDtoTransformer.toDto(it) }.toTypedArray()).build()
    }
}
