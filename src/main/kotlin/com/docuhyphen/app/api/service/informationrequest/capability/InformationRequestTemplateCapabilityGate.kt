package com.docuhyphen.app.api.service.informationrequest.capability

import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapabilityRequirement
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestTemplateCapabilityGate @Inject constructor(
    private val executors: InformationRequestCapabilityExecutorRegistry,
    private val recordedCapabilities: InformationRequestTemplateVersionCapabilityRepository,
)
{
    fun requirementsOf(templateVersionId: UUID): List<InformationRequestCapabilityRequirement> =
        recordedCapabilities.findForVersion(templateVersionId).map { recorded ->
            InformationRequestCapabilityRequirement(
                capability = recorded.capabilityKey,
                requiredContractVersion = recorded.requiredContractVersion,
            )
        }

    fun unservedRequirements(templateVersionId: UUID): List<InformationRequestCapabilityRequirement> =
        executors.unserved(requirementsOf(templateVersionId))

    fun requireInstalledCapabilities(templateVersionId: UUID)
    {
        val unserved = unservedRequirements(templateVersionId)
        if (unserved.isEmpty())
        {
            return
        }

        val described = unserved.joinToString(", ") { "${it.capability} v${it.requiredContractVersion}" }
        throw InformationRequestCapabilityNotInstalledException(
            "This deployment does not serve every runtime capability the information request " +
                    "template version requires: $described",
            unserved,
        )
    }
}
