package com.docuhyphen.app.api.service.variable

import com.docuhyphen.app.api.model.dto.AvailableVariablesDto
import com.docuhyphen.app.api.model.dto.SequenceDefinitionDto
import com.docuhyphen.app.api.model.dto.SystemVariableDto
import com.docuhyphen.app.api.model.dto.VariableDefinitionDto
import com.docuhyphen.app.api.model.dto.toDto
import com.docuhyphen.app.api.model.entity.VariableScope
import com.docuhyphen.app.api.repository.SequenceDefinitionRepository
import com.docuhyphen.app.api.repository.VariableDefinitionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class AvailableVariablesService @Inject constructor(
    private val sequenceRepository: SequenceDefinitionRepository,
    private val variableRepository: VariableDefinitionRepository,
)
{
    companion object
    {
        val SYSTEM_VARIABLES = listOf(
            SystemVariableDto("USER_FIRST_NAME", "Initiator's first name", "Jane"),
            SystemVariableDto("USER_LAST_NAME", "Initiator's last name", "Smith"),
            SystemVariableDto("USER_FULL_NAME", "Initiator's full name", "Jane Smith"),
            SystemVariableDto("USER_EMAIL", "Initiator's email address", "jane@example.com"),
            SystemVariableDto("ORG_NAME", "Your organization's name", "Acme Corp"),
            SystemVariableDto("ORG_REG_NUMBER", "Your organization's registration number", "2024/123456/07"),
            SystemVariableDto("CURRENT_DATE", "Today's date (yyyy-MM-dd)", "2026-06-21"),
            SystemVariableDto("CURRENT_YEAR", "Current year", "2026"),
            SystemVariableDto("CURRENT_MONTH", "Current month name", "June"),
            SystemVariableDto("CURRENT_MONTH_NUMBER", "Current month number (01–12)", "06"),
        )
    }

    fun getAvailableVariables(
        callerUserId: UUID,
        callerOrgId: UUID?,
    ): AvailableVariablesDto
    {
        val sequences: List<SequenceDefinitionDto> = callerOrgId
            ?.let { sequenceRepository.findAllByOrganizationIdAndIsDeletedFalse(it).filter { s -> s.isActive }.map { s -> s.toDto() } }
            ?: emptyList()

        val orgVariables: List<VariableDefinitionDto> = callerOrgId
            ?.let { variableRepository.findByScopeAndOrganizationIdAndIsDeletedFalse(VariableScope.ORG, it).filter { v -> v.isActive }.map { v -> v.toDto() } }
            ?: emptyList()

        val personalVariables: List<VariableDefinitionDto> =
            variableRepository.findByScopeAndCreatedByAppUserIdAndIsDeletedFalse(VariableScope.PERSONAL, callerUserId)
                .filter { it.isActive }
                .map { it.toDto() }

        return AvailableVariablesDto(
            system = SYSTEM_VARIABLES,
            sequences = sequences,
            org = orgVariables,
            personal = personalVariables,
        )
    }
}
