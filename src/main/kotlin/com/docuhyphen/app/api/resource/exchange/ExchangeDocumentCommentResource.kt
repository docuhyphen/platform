package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.resource.model.CommentRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentCommentsService
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("exchanges/{exchangeId}/documents/{documentId}/comments")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class ExchangeDocumentCommentResource @Inject constructor(
    private val documentCommentsService: ExchangeDocumentCommentsService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentCommentResource::class.java)
    }

    @POST
    @Transactional
    fun addComment(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String,
        commentRequest: CommentRequest,
    ): Response
    {
        return try
        {
            val comment = documentCommentsService.addDocumentComment(
                exchangeId,
                documentId,
                commentRequest.commentText,
                commentRequest.isInternal,
                commentRequest.pageNumber,
                commentRequest.documentVersionId,
            )

            Response.status(Response.Status.CREATED)
                .entity(DetailedEntityToDtoTransformer.toDto(comment))
                .build()
        }
        catch (exception: SubscriptionDenialException)
        {
            logger.warn(
                "Adding a document comment was refused by the subscription plan check: plan={}",
                exception.denial.planCode,
            )
            throw exception
        }
        catch (exception: Exception)
        {
            logger.error("Error adding document comment", exception)
            val status = when (exception)
            {
                is WebApplicationException -> exception.response.status
                is IllegalArgumentException -> Response.Status.BAD_REQUEST.statusCode
                else -> Response.Status.INTERNAL_SERVER_ERROR.statusCode
            }
            val message = if (status >= 500) "An error occurred while adding the document comment"
            else exception.message
            Response.status(status).entity(ResponseError(message)).build()
        }
    }

    @GET
    fun getComments(
        @PathParam("exchangeId") exchangeId: String,
        @PathParam("documentId") documentId: String
    ): Response
    {

        return try
        {
            val comments = documentCommentsService.getDocumentComments(exchangeId, documentId)
            Response.ok(comments.map { DetailedEntityToDtoTransformer.toDto(it) }.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error retrieving document comments", exception)
            val status = when (exception)
            {
                is WebApplicationException -> exception.response.status
                is IllegalArgumentException -> Response.Status.BAD_REQUEST.statusCode
                else -> Response.Status.INTERNAL_SERVER_ERROR.statusCode
            }
            val message = if (status >= 500) "An error occurred while retrieving document comments"
            else exception.message
            Response.status(status).entity(ResponseError(message)).build()
        }
    }
}
