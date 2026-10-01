package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InformationRequestTitleReaderTest
{
    private val versionRepository = mock<InformationRequestTemplateVersionRepository>()
    private val definitionRepository = mock<InformationRequestTemplateDefinitionRepository>()
    private val reader = InformationRequestTitleReader(versionRepository, definitionRepository)

    @Test
    fun `each request is titled by the Template Definition its pinned Version belongs to`()
    {
        val periodic = InformationRequestTemplateDefinition().apply { displayName = "Periodic records request" }
        val supporting = InformationRequestTemplateDefinition().apply { displayName = "Supporting documents request" }
        val periodicVersion = InformationRequestTemplateVersion().apply { templateDefinitionId = periodic.id }
        val supportingVersion = InformationRequestTemplateVersion().apply { templateDefinitionId = supporting.id }
        val first = InformationRequest().apply { templateVersionId = periodicVersion.id }
        val second = InformationRequest().apply { templateVersionId = supportingVersion.id }
        val third = InformationRequest().apply { templateVersionId = periodicVersion.id }
        whenever(versionRepository.findForIds(any())).thenReturn(listOf(periodicVersion, supportingVersion))
        whenever(definitionRepository.findForIds(any())).thenReturn(listOf(periodic, supporting))

        val titles = reader.titlesOf(listOf(first, second, third))

        assertEquals(
            mapOf(
                first.id to "Periodic records request",
                second.id to "Supporting documents request",
                third.id to "Periodic records request",
            ),
            titles,
        )
        assertEquals("Supporting documents request", reader.titleOf(second))
    }
}
