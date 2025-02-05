package com.dochyphen.app.api.resource

import com.dochyphen.app.api.service.SharingSessionDocumentService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/documents/{documentId}/comments")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class DocumentCommentResource @Inject constructor(
    private val sharingSessionDocumentService: SharingSessionDocumentService
) {

    @POST
    fun addComment(
        @PathParam("documentId") documentId: String,
        commentRequest: CommentRequest
    ): Response {
        val comment = sharingSessionDocumentService.addDocumentComment(documentId, commentRequest.commentText, commentRequest.commentedBy)
        return Response.ok(comment).build()
    }

    @GET
    fun getComments(@PathParam("documentId") documentId: String): Response {
        val comments = sharingSessionDocumentService.getDocumentComments(documentId)
        return Response.ok(comments).build()
    }
}

data class CommentRequest(
    val commentText: String,
    val commentedBy: String
)