package com.dochyphen.app.api.resource

import com.dochyphen.app.api.model.DetailedEntityToDtoTransformer
import com.dochyphen.app.api.resource.model.CommentRequest
import com.dochyphen.app.api.service.sharingsession.SharingSessionDocumentCommentsService
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("sharing-sessions/{sessionId}/documents/{documentId}/comments")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class SharingSessionDocumentCommentResource @Inject constructor(
    private val documentCommentsService: SharingSessionDocumentCommentsService
)
{
    @POST
    @Transactional
    fun addComment(
        @PathParam("sessionId") sessionId: String,
        @PathParam("documentId") documentId: String,
        commentRequest: CommentRequest,
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(300, 600)

        val comment = documentCommentsService.addDocumentComment(
            sessionId,
            documentId,
            commentRequest.commentText,
            commentRequest.commentedBy
        )

        return Response.ok(DetailedEntityToDtoTransformer.toDto(comment)).build()
    }

    @GET
    fun getComments(
        @PathParam("documentId") documentId: String
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(300, 600)

        val comments = documentCommentsService.getDocumentComments(documentId)

        return Response.ok(comments.map { DetailedEntityToDtoTransformer.toDto(it) }.toTypedArray()).build()
    }
}