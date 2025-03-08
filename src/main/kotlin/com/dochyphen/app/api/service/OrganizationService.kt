package com.dochyphen.app.api.service

import com.dochyphen.app.api.model.entity.Organization
import com.dochyphen.app.api.exception.OrganizationNotFoundException
import com.dochyphen.app.api.repository.OrganizationRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID

@RequestScoped
class OrganizationService @Inject constructor(
    private val organizationRepository: OrganizationRepository
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationService::class.java)
    }

    fun getOrganizationByAppUserIdAndPersonId(appUserId: UUID, personId: UUID): Organization
    {
        return organizationRepository.findByAppUserIdAndPersonId(appUserId, personId)
            ?: throw OrganizationNotFoundException("Organization not found for appUserId: $appUserId and personId: $personId")
    }
}