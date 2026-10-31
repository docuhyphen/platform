package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentTargetResolver
import com.docuhyphen.app.api.service.informationrequest.creation.InformationRequestAdHocCreationService
import jakarta.enterprise.context.ApplicationScoped
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InformationRequestTemplateCapabilityWriterTest {
    @Test
    fun `recorded capabilities have one writer that every publishing path reaches by injection`() {
        listOf(
            InformationRequestTemplatePublicationService::class.java,
            InformationRequestPrivateVersionPublisher::class.java,
            InformationRequestAdHocCreationService::class.java,
            InformationRequestAmendmentTargetResolver::class.java,
        ).forEach { type ->
            assertFalse(
                type.constructors.any { InformationRequestTemplateVersionCapabilityRepository::class.java in it.parameterTypes },
                "${type.simpleName} receives the capability repository",
            )
        }
        assertTrue(
            InformationRequestTemplateVersionFreezer::class.java.constructors.single().parameterTypes
                .contains(InformationRequestTemplateVersionCapabilityRepository::class.java),
        )
        assertTrue(InformationRequestPrivateVersionPublisher::class.java.isAnnotationPresent(ApplicationScoped::class.java))
        listOf(
            InformationRequestAdHocCreationService::class.java,
            InformationRequestAmendmentTargetResolver::class.java
        )
            .forEach { type ->
                assertTrue(
                    type.constructors.any { InformationRequestPrivateVersionPublisher::class.java in it.parameterTypes },
                    "${type.simpleName} is handed the private publisher",
                )
            }
        assertTrue(
            InformationRequestTemplatePublicationService::class.java.constructors
                .any { InformationRequestTemplateVersionFreezer::class.java in it.parameterTypes },
        )
    }
}
