package com.docuhyphen.app.api.resource.fields

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.resource.fields.operations.SchemaResourceOperations
import com.docuhyphen.app.api.resource.model.CreateSchemaRequest
import com.docuhyphen.app.api.resource.model.FieldStatusRequest
import com.docuhyphen.app.api.resource.model.PublishSchemaRequest
import com.docuhyphen.app.api.resource.model.UpdateBindingsRequest
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.SchemaDefinitionService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class SchemaResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val schemaDefinitionService: SchemaDefinitionService,
) : SchemaResourceOperations
{
    override fun listSchemas(scopeKindParam: String?): Response
    {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            val scopeKind = FieldsHttp.scopeKind(scopeKindParam)
            Response.ok(schemaDefinitionService.listSchemas(scopeKind).toTypedArray()).build()
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to list schemas for scope {}", scopeKindParam, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun createSchema(request: CreateSchemaRequest): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            Response.status(Response.Status.CREATED).entity(schemaDefinitionService.createSchema(request)).build()
        } catch (exception: SubscriptionDenialException) {
            logger.warn("Schema creation refused by subscription policy")
            throw exception
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to create a schema", exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun getSchema(id: String): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            Response.ok(schemaDefinitionService.getSchema(FieldsHttp.uuid(id, "schema id"))).build()
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to read schema {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun getVersion(versionId: String): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            Response.ok(schemaDefinitionService.getVersion(FieldsHttp.uuid(versionId, "schema version id"))).build()
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to read schema version {}", versionId, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun getResolved(id: String): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            val resolved = schemaDefinitionService.getResolvedLatestPublished(FieldsHttp.uuid(id, "schema id"))
            Response.ok(resolved).build()
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to read the resolved published version of schema {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun updateDraftBindings(id: String, request: UpdateBindingsRequest): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            val schemaId = FieldsHttp.uuid(id, "schema id")
            Response.ok(schemaDefinitionService.updateDraftBindings(schemaId, request.bindings)).build()
        } catch (exception: SubscriptionDenialException) {
            logger.warn("Draft binding change of schema {} refused by subscription policy", id)
            throw exception
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to replace the draft bindings of schema {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun publishDraft(id: String, request: PublishSchemaRequest?): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            val schemaId = FieldsHttp.uuid(id, "schema id")
            Response.ok(schemaDefinitionService.publishDraft(schemaId, request?.compatibility)).build()
        } catch (exception: SubscriptionDenialException) {
            logger.warn("Draft publication of schema {} refused by subscription policy", id)
            throw exception
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to publish the draft of schema {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun createDraftVersion(id: String): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            val schemaId = FieldsHttp.uuid(id, "schema id")
            Response.status(Response.Status.CREATED).entity(schemaDefinitionService.createDraftVersion(schemaId))
                .build()
        } catch (exception: SubscriptionDenialException) {
            logger.warn("New draft version of schema {} refused by subscription policy", id)
            throw exception
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to open a new draft version of schema {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun patchStatus(id: String, request: FieldStatusRequest): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            val schemaId = FieldsHttp.uuid(id, "schema id")
            if (request.status != FieldLifecycleStatus.RETIRED)
                throw FieldValidationException("Only RETIRED is supported")
            Response.ok(schemaDefinitionService.retireSchema(schemaId)).build()
        } catch (exception: SubscriptionDenialException) {
            logger.warn("Status change of schema {} refused by subscription policy", id)
            throw exception
        } catch (exception: Exception)
        {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to change the status of schema {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SchemaResource::class.java)
    }
}
