package com.docuhyphen.app.api.service.informationrequest.workflow

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.CanonicalFieldValue
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.workflow.WorkflowRequirementOperand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionPackageRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestWorkflowOperandServiceTest
{
    private val packages = mock<InformationRequestSubmissionPackageRepository>()
    private val items = mock<InformationRequestSubmissionItemRepository>()
    private val requirements = mock<InformationRequestRequirementRepository>()
    private val revisions = mock<FieldValueRevisionQueryService>()
    private val bindings = mock<InformationRequestTemplateRequirementBindingRepository>()
    private val service = InformationRequestWorkflowOperandService(packages, items, requirements, revisions, bindings)
    private val requestId = UUID.randomUUID()
    private val packageId = UUID.randomUUID()
    private val templateRequirementId = UUID.randomUUID()

    @Test
    fun `the operand is the answer frozen in the exact package of the named request`()
    {
        val revisionId = UUID.randomUUID()
        givenPackage()
        givenItems(item(templateRequirementId, "root", revisionId))
        whenever(revisions.canonicalValueOf(revisionId))
            .thenReturn(CanonicalFieldValue(FieldValueType.SHORT_TEXT, false, textValue = "Recorded answer"))

        val operand = service.frozenValue(requestId, packageId, templateRequirementId, "root")

        assertEquals(
            WorkflowRequirementOperand.Value(CanonicalFieldValue(FieldValueType.SHORT_TEXT, false, textValue = "Recorded answer")),
            operand,
        )
    }

    @Test
    fun `a package of another request, an absent item, and another occurrence are unavailable`()
    {
        whenever(packages.findById(packageId)).thenReturn(InformationRequestSubmissionPackage().apply {
            id = packageId
            informationRequestId = UUID.randomUUID()
        })
        assertEquals(
            WorkflowRequirementOperand.Unavailable(InformationRequestWorkflowOperandService.PACKAGE_MISMATCH),
            service.frozenValue(requestId, packageId, templateRequirementId, "root"),
        )

        givenPackage()
        givenItems(item(UUID.randomUUID(), "root", UUID.randomUUID()), item(templateRequirementId, "group/1", UUID.randomUUID()))
        assertEquals(
            WorkflowRequirementOperand.Unavailable(InformationRequestWorkflowOperandService.ITEM_ABSENT),
            service.frozenValue(requestId, packageId, templateRequirementId, "root"),
        )
    }

    @Test
    fun `two frozen items for one stable requirement and occurrence are ambiguous and a non-field item is refused`()
    {
        givenPackage()
        givenItems(item(templateRequirementId, "root", UUID.randomUUID()), item(templateRequirementId, "root", UUID.randomUUID()))
        assertEquals(
            WorkflowRequirementOperand.Unavailable(InformationRequestWorkflowOperandService.AMBIGUOUS),
            service.frozenValue(requestId, packageId, templateRequirementId, "root"),
        )

        givenItems(item(templateRequirementId, "root", null, InformationRequestRequirementType.DOCUMENT))
        assertEquals(
            WorkflowRequirementOperand.Unavailable(InformationRequestWorkflowOperandService.NOT_A_FIELD),
            service.frozenValue(requestId, packageId, templateRequirementId, "root"),
        )
    }

    @Test
    fun `an item frozen without an answer is empty and carries the type of the Field it collects`()
    {
        givenPackage()
        val unanswered = item(templateRequirementId, "root", null)
        givenItems(unanswered)
        givenFieldType(unanswered, FieldValueType.INTEGER)

        assertEquals(
            WorkflowRequirementOperand.Empty(FieldValueType.INTEGER),
            service.frozenValue(requestId, packageId, templateRequirementId, "root"),
        )
    }

    @Test
    fun `a cleared answer is empty of its recorded type and an unanswered item whose Field type cannot be resolved is unavailable`() {
        givenPackage()
        val revisionId = UUID.randomUUID()
        givenItems(item(templateRequirementId, "root", revisionId))
        whenever(revisions.canonicalValueOf(revisionId)).thenReturn(CanonicalFieldValue.empty(FieldValueType.DATE))
        assertEquals(
            WorkflowRequirementOperand.Empty(FieldValueType.DATE),
            service.frozenValue(requestId, packageId, templateRequirementId, "root"),
        )

        givenItems(item(templateRequirementId, "root", null))
        assertEquals(
            WorkflowRequirementOperand.Unavailable(InformationRequestWorkflowOperandService.VALUE_TYPE_UNRESOLVED),
            service.frozenValue(requestId, packageId, templateRequirementId, "root"),
        )
    }

    private fun givenFieldType(frozen: InformationRequestSubmissionItem, type: FieldValueType) {
        val fieldDefinitionId = UUID.randomUUID()
        whenever(bindings.findById(frozen.templateBindingId)).thenReturn(
            InformationRequestTemplateRequirementBinding().apply {
                id = frozen.templateBindingId
                collectedFieldDefinitionId = fieldDefinitionId
            },
        )
        whenever(revisions.valueTypeOfDefinition(fieldDefinitionId)).thenReturn(type)
    }

    private fun givenPackage()
    {
        whenever(packages.findById(packageId)).thenReturn(InformationRequestSubmissionPackage().apply {
            id = packageId
            informationRequestId = requestId
        })
    }

    private fun givenItems(vararg frozen: InformationRequestSubmissionItem)
    {
        whenever(items.findForPackages(listOf(packageId))).thenReturn(frozen.toList())
    }

    private fun item(
        stableRequirementId: UUID,
        path: String,
        revisionId: UUID?,
        type: InformationRequestRequirementType = InformationRequestRequirementType.FIELD,
    ): InformationRequestSubmissionItem
    {
        val runtimeRequirementId = UUID.randomUUID()
        whenever(requirements.findById(runtimeRequirementId)).thenReturn(InformationRequestRequirement().apply {
            id = runtimeRequirementId
            sourceTemplateRequirementId = stableRequirementId
        })
        return InformationRequestSubmissionItem().apply {
            this.packageId = this@InformationRequestWorkflowOperandServiceTest.packageId
            informationRequestId = requestId
            informationRequestRequirementId = runtimeRequirementId
            requirementRevisionId = UUID.randomUUID()
            templateBindingId = UUID.randomUUID()
            requirementKey = "recorded-note"
            requirementType = type
            occurrencePath = path
            completenessState = InformationRequestCompletenessItemState.values().first()
            fieldValueRevisionId = revisionId
            itemHashSha256 = "f".repeat(64)
        }
    }
}
