package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Decides whether this deployment can serve what a Template Version needs.
 *
 * Publication already settled which capabilities the Version requires and at which contract
 * versions. This asks the separate question of whether those are installed here, and it is asked
 * when a request is created against the Version rather than when the Version freezes. Configuration
 * outlives any one deployment: a Version published on an installation that serves everything must
 * stay publishable on one that does not yet, and refusing publication would make the Template
 * unauthorable until the runtime caught up.
 */
@ApplicationScoped
class InformationRequestTemplateCapabilityGate @Inject constructor(
    private val executors: InformationRequestCapabilityExecutorRegistry,
    private val recordedCapabilities: InformationRequestTemplateVersionCapabilityRepository,
)
{
    /** What the Version recorded needing, in the order the repository returns. */
    fun requirementsOf(templateVersionId: UUID): List<InformationRequestCapabilityRequirement> =
        recordedCapabilities.findForVersion(templateVersionId).map { recorded ->
            InformationRequestCapabilityRequirement(
                capability = recorded.capabilityKey,
                requiredContractVersion = recorded.requiredContractVersion,
            )
        }

    /** Every requirement of the Version this deployment cannot serve, empty when it can serve all. */
    fun unservedRequirements(templateVersionId: UUID): List<InformationRequestCapabilityRequirement> =
        executors.unserved(requirementsOf(templateVersionId))

    /**
     * @throws InformationRequestCapabilityNotInstalledException when any recorded requirement is
     * not served here. Naming every unserved requirement rather than the first lets an operator see
     * the whole gap in one refusal.
     */
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
