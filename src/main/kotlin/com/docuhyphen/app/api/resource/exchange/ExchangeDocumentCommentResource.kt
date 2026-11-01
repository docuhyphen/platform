package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.resource.exchange.operations.ExchangeDocumentCommentResourceOperations
import com.docuhyphen.app.api.resource.model.CommentRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentCommentsService
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class ExchangeDocumentCommentResource @Inject constructor(
    private val documentCommentsService: ExchangeDocumentCommentsService
) : ExchangeDocumentCommentResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentCommentResource::class.java)
    }

    override fun addComment(
        exchangeId: String,
        documentId: String,
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

            Response.status(CREATED)
                .entity(DetailedEntityToDtoTransformer.toDto(comment))
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Adding a document comment was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                is WebApplicationException ->
                {
                    val status = exception.response.status
                    val message =
                        if (status >= INTERNAL_SERVER_ERROR.statusCode) "An error occurred while adding the document comment"
                        else exception.message
                    Response.status(status).entity(ResponseError(message)).build()
                }

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error adding Exchange document comment", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while adding the document comment"))
                        .build()
                }
            }
        }
    }

    override fun getComments(
        exchangeId: String,
        documentId: String
    ): Response
    {
        return try
        {
            val comments = documentCommentsService.getDocumentComments(exchangeId, documentId)
            Response.ok(comments.map { DetailedEntityToDtoTransformer.toDto(it) }.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException ->
                {
                    val status = exception.response.status
                    val message =
                        if (status >= INTERNAL_SERVER_ERROR.statusCode) "An error occurred while retrieving document comments"
                        else exception.message
                    Response.status(status).entity(ResponseError(message)).build()
                }

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error retrieving Exchange document comments", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while retrieving document comments"))
                        .build()
                }
            }
        }
    }
}
