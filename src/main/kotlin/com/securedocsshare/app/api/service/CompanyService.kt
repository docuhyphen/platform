package com.securedocsshare.app.api.service

import com.securedocsshare.app.api.model.Company
import com.securedocsshare.app.api.model.CompanyNotFoundException
import com.securedocsshare.app.api.repository.CompanyRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID

@RequestScoped
class CompanyService @Inject constructor(
    private val companyRepository: CompanyRepository
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(CompanyService::class.java)
    }

    fun getCompanyByAppUserIdAndPersonId(appUserId: UUID, personId: UUID): Company
    {
        return companyRepository.findByAppUserIdAndPersonId(appUserId, personId)
            ?: throw CompanyNotFoundException("Company not found for appUserId: $appUserId and personId: $personId")
    }
}