package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.fields.*
import com.docuhyphen.app.api.resource.exchange.operations.ExchangeFieldsResourceOperations
import com.docuhyphen.app.api.resource.fields.FieldsHttp
import com.docuhyphen.app.api.resource.fields.FieldsPreconditionHeader
import com.docuhyphen.app.api.resource.model.AssignSchemaRequest
import com.docuhyphen.app.api.resource.model.SetFieldValuesRequest
import com.docuhyphen.app.api.service.fields.FieldsAccessContextFactory
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class ExchangeFieldsResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val schemaAssignmentService: SchemaAssignmentService,
    private val fieldsAccessContextFactory: FieldsAccessContextFactory,
) : ExchangeFieldsResourceOperations {
    override fun getSchema(id: String): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            val state = schemaAssignmentService.getAssignmentState(
                FieldValueReadCommand(FieldsResourceRef(RESOURCE_TYPE, FieldsHttp.uuid(id, "Exchange id")), fieldsAccessContextFactory.current()),
            )
            FieldsHttp.withETag(Response.ok(state), state.assignment?.etag)
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to read the schema assignment of Exchange {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun assignSchema(
        id: String,
        request: AssignSchemaRequest,
        ifMatch: String?,
        ifNoneMatch: String?,
    ): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            val assignment = schemaAssignmentService.applySchemaAssignment(
                SchemaAssignmentCommand(
                    resource = FieldsResourceRef(RESOURCE_TYPE, FieldsHttp.uuid(id, "Exchange id")),
                    access = fieldsAccessContextFactory.current(),
                    operation = SchemaAssignmentOperation.ASSIGN,
                    schemaDefinitionId = request.schemaDefinitionId,
                    precondition = FieldsPreconditionHeader.assignment(ifMatch, ifNoneMatch),
                ),
            )
            if (assignment == null) Response.noContent().build()
            else FieldsHttp.withETag(Response.ok(assignment), assignment.etag)
        } catch (exception: SubscriptionDenialException) {
            logger.warn("Schema assignment to Exchange {} refused by subscription policy", id)
            throw exception
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to assign a schema to Exchange {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun unassignSchema(id: String, ifMatch: String?): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            schemaAssignmentService.applySchemaAssignment(
                SchemaAssignmentCommand(
                    resource = FieldsResourceRef(RESOURCE_TYPE, FieldsHttp.uuid(id, "Exchange id")),
                    access = fieldsAccessContextFactory.current(),
                    operation = SchemaAssignmentOperation.UNASSIGN,
                    precondition = FieldsPreconditionHeader.required(ifMatch),
                ),
            )
            Response.noContent().build()
        } catch (exception: SubscriptionDenialException) {
            logger.warn("Schema removal from Exchange {} refused by subscription policy", id)
            throw exception
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to remove the schema from Exchange {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun patchValues(id: String, request: SetFieldValuesRequest, ifMatch: String?): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            val assignment = schemaAssignmentService.setValues(
                FieldValueWriteCommand(
                    resource = FieldsResourceRef(RESOURCE_TYPE, FieldsHttp.uuid(id, "Exchange id")),
                    access = fieldsAccessContextFactory.current(),
                    entries = request.values,
                    precondition = FieldsPreconditionHeader.required(ifMatch),
                ),
            )
            FieldsHttp.withETag(Response.ok(assignment), assignment.etag)
        } catch (exception: SubscriptionDenialException) {
            logger.warn("Field value save on Exchange {} refused by subscription policy", id)
            throw exception
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to save the field values of Exchange {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    private companion object {
        const val RESOURCE_TYPE = "EXCHANGE"
        val logger = LoggerFactory.getLogger(ExchangeFieldsResource::class.java)
    }
}
