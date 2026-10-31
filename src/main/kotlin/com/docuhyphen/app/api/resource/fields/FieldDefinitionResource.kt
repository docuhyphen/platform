package com.docuhyphen.app.api.resource.fields

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.resource.fields.operations.FieldDefinitionResourceOperations
import com.docuhyphen.app.api.resource.model.CreateFieldDefinitionRequest
import com.docuhyphen.app.api.resource.model.FieldContractRequest
import com.docuhyphen.app.api.resource.model.FieldStatusRequest
import com.docuhyphen.app.api.service.fields.FieldDefinitionService
import com.docuhyphen.app.api.service.fields.FieldValidationException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class FieldDefinitionResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val fieldDefinitionService: FieldDefinitionService,
) : FieldDefinitionResourceOperations
{
    override fun listTypes(): Response
    {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            Response.ok(fieldDefinitionService.listTypes().toTypedArray()).build()
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to list the field value types", exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun listDefinitions(scopeKindParam: String?): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            val scopeKind = FieldsHttp.scopeKind(scopeKindParam)
            Response.ok(fieldDefinitionService.listDefinitions(scopeKind).toTypedArray()).build()
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to list field definitions for scope {}", scopeKindParam, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun createDefinition(request: CreateFieldDefinitionRequest): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            Response.status(Response.Status.CREATED).entity(fieldDefinitionService.createDefinition(request)).build()
        } catch (exception: SubscriptionDenialException) {
            logger.warn("Field definition creation refused by subscription policy")
            throw exception
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to create a field definition", exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun getDefinition(id: String): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            Response.ok(fieldDefinitionService.getDefinition(FieldsHttp.uuid(id, "definition id"))).build()
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to read field definition {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun listContracts(id: String): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            val contracts = fieldDefinitionService.listContracts(FieldsHttp.uuid(id, "definition id"))
            Response.ok(contracts.toTypedArray()).build()
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to list the contracts of field definition {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun addContract(id: String, request: FieldContractRequest): Response {
        return try {
            FieldsHttp.requireUser(authTokenContext)
            val contract = fieldDefinitionService.addContractVersion(FieldsHttp.uuid(id, "definition id"), request)
            Response.status(Response.Status.CREATED).entity(contract).build()
        } catch (exception: SubscriptionDenialException) {
            logger.warn("New contract for field definition {} refused by subscription policy", id)
            throw exception
        } catch (exception: Exception) {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to add a contract to field definition {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    override fun patchStatus(id: String, request: FieldStatusRequest): Response
    {
        return try
        {
            FieldsHttp.requireUser(authTokenContext)
            val definitionId = FieldsHttp.uuid(id, "definition id")
            if (request.status != FieldLifecycleStatus.RETIRED)
                throw FieldValidationException("Only RETIRED is supported")
            Response.ok(fieldDefinitionService.retireDefinition(definitionId)).build()
        } catch (exception: SubscriptionDenialException)
        {
            logger.warn("Status change of field definition {} refused by subscription policy", id)
            throw exception
        } catch (exception: Exception)
        {
            FieldsHttp.refusal(exception) ?: run {
                logger.error("Failed to change the status of field definition {}", id, exception)
                FieldsHttp.failed()
            }
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(FieldDefinitionResource::class.java)
    }
}
