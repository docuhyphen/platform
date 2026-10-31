package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestPrivateVersionPublisherTest {
    private val actor = PrincipalRef(PrincipalKind.APPLICATION, UUID.randomUUID())
    private val configuration = InformationRequestTemplateConfigurationRequest(schemaVersionId = UUID.randomUUID())
    private val definition = InformationRequestTemplateDefinition().apply {
        scopeKind = InformationRequestTemplateScopeKind.PERSONAL
        scopeUserId = UUID.randomUUID()
        namespace = "ad-hoc"
        templateKey = "request-one"
        displayName = "Process collection"
    }
    private val versionRepository = mock<InformationRequestTemplateVersionRepository>().also {
        whenever(it.save(any())).thenAnswer { invocation -> invocation.getArgument(0) }
    }
    private val configurationWriter = mock<InformationRequestTemplateConfigurationWriter>()
    private val schemaCompatibility = mock<InformationRequestTemplateSchemaCompatibility>()
    private val freezer = mock<InformationRequestTemplateVersionFreezer>()
    private val publisher = InformationRequestPrivateVersionPublisher(
        versionRepository,
        configurationWriter,
        schemaCompatibility,
        freezer,
    )

    @Test
    fun `a private version is written whole and then frozen by the shared version freezer`() {
        val published = publisher.publish(definition, 4, configuration, actor)

        assertEquals(definition.id, published.templateDefinitionId)
        assertEquals(4, published.versionNumber)
        assertEquals(actor.kind, published.createdByPrincipalKind)
        assertEquals(actor.id, published.createdByPrincipalId)
        inOrder(schemaCompatibility, configurationWriter, freezer, versionRepository) {
            verify(schemaCompatibility).requireUsable(definition, configuration.schemaVersionId)
            verify(configurationWriter).replaceConfiguration(published, configuration)
            verify(freezer).freeze(definition, published, actor)
            verify(versionRepository).flushChanges()
        }
    }

    @Test
    fun `a schema the owner cannot use stops the private version before it is written`() {
        doThrow(InformationRequestTemplateValidationException("Schema version belongs to a different owner"))
            .whenever(schemaCompatibility).requireUsable(definition, configuration.schemaVersionId)

        assertThrows<InformationRequestTemplateValidationException> {
            publisher.publish(definition, 2, configuration, actor)
        }

        verify(configurationWriter, never()).replaceConfiguration(any<InformationRequestTemplateVersion>(), any())
        verifyNoInteractions(freezer)
    }
}
