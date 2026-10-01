package com.docuhyphen.app.api.service.informationrequest.workflow

import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.workflow.WorkflowRequirementOperand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.workflow.WorkflowRequirementOperandSource
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestWorkflowOperandService @Inject constructor(
    private val packageRepository: InformationRequestSubmissionPackageRepository,
    private val itemRepository: InformationRequestSubmissionItemRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val fieldValueRevisions: FieldValueRevisionQueryService,
) : WorkflowRequirementOperandSource
{
    override fun frozenValue(
        requestId: UUID,
        packageId: UUID,
        templateRequirementId: UUID,
        occurrencePath: String,
    ): WorkflowRequirementOperand
    {
        packageRepository.findById(packageId)?.takeIf { it.informationRequestId == requestId }
            ?: return WorkflowRequirementOperand.Unavailable(PACKAGE_MISMATCH)
        val matching = itemRepository.findForPackages(listOf(packageId)).filter { item ->
            item.occurrencePath == occurrencePath &&
                    requirementRepository.findById(item.informationRequestRequirementId)?.sourceTemplateRequirementId == templateRequirementId
        }
        val item = when (matching.size)
        {
            0 -> return WorkflowRequirementOperand.Unavailable(ITEM_ABSENT)
            1 -> matching.single()
            else -> return WorkflowRequirementOperand.Unavailable(AMBIGUOUS)
        }
        if (item.requirementType != InformationRequestRequirementType.FIELD)
        {
            return WorkflowRequirementOperand.Unavailable(NOT_A_FIELD)
        }
        val revisionId = item.fieldValueRevisionId ?: return WorkflowRequirementOperand.Empty
        val value = fieldValueRevisions.canonicalValueOf(revisionId)
            ?: return WorkflowRequirementOperand.Unavailable(VALUE_UNAVAILABLE)
        return if (value.isEmpty) WorkflowRequirementOperand.Empty else WorkflowRequirementOperand.Value(value)
    }

    companion object
    {
        const val PACKAGE_MISMATCH = "PACKAGE_MISMATCH"
        const val ITEM_ABSENT = "ITEM_ABSENT"
        const val AMBIGUOUS = "AMBIGUOUS"
        const val NOT_A_FIELD = "NOT_A_FIELD"
        const val VALUE_UNAVAILABLE = "VALUE_UNAVAILABLE"
    }
}
