package com.dochyphen.app.api.resource

import com.dochyphen.app.api.model.entity.DetailedModelConverter
import com.dochyphen.app.api.resource.model.CommentRequest
import com.dochyphen.app.api.service.sharingsession.SharingSessionDocumentCommentsService
import jakarta.inject.Inject
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
    fun addComment(
        @PathParam("documentId") documentId: String,
        commentRequest: CommentRequest
    ): Response
    {
        val comment = documentCommentsService.addDocumentComment(
            documentId,
            commentRequest.commentText,
            commentRequest.commentedBy
        )

        return Response.ok(DetailedModelConverter.toDto(comment)).build()
    }

    @GET
    fun getComments(
        @PathParam("documentId") documentId: String
    ): Response
    {
        val comments = documentCommentsService.getDocumentComments(documentId)

        return Response.ok(comments.map { DetailedModelConverter.toDto(it) }.toTypedArray()).build()
    }
}