package com.docuhyphen.app.api.service.informationrequest.capability

import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapabilityRequirement

interface InformationRequestCapabilityExecutor
{
    val capability: InformationRequestCapability

    val contractVersion: Int

    val minimumSupportedContractVersion: Int
}

class InstalledCapabilities(executors: Iterable<InformationRequestCapabilityExecutor>)
{
    private val index: Map<InformationRequestCapability, InformationRequestCapabilityExecutor> =
        buildIndex(executors)

    fun installedCapabilities(): Set<InformationRequestCapability> = index.keys

    fun serves(requirement: InformationRequestCapabilityRequirement): Boolean
    {
        val executor = index[requirement.capability] ?: return false
        return requirement.requiredContractVersion >= executor.minimumSupportedContractVersion &&
                requirement.requiredContractVersion <= executor.contractVersion
    }

    fun unserved(
        requirements: Iterable<InformationRequestCapabilityRequirement>,
    ): List<InformationRequestCapabilityRequirement> = requirements.filterNot(::serves)

    private fun buildIndex(
        executors: Iterable<InformationRequestCapabilityExecutor>,
    ): Map<InformationRequestCapability, InformationRequestCapabilityExecutor>
    {
        val indexed = mutableMapOf<InformationRequestCapability, InformationRequestCapabilityExecutor>()
        for (executor in executors)
        {
            val capability = executor.capability
            val name = executor::class.qualifiedName
            check(capability !in indexed) {
                "Duplicate InformationRequestCapabilityExecutor for $capability: " +
                        "${indexed[capability]!!::class.qualifiedName} vs $name"
            }
            check(executor.minimumSupportedContractVersion >= 1) {
                "InformationRequestCapabilityExecutor $name serves $capability from contract " +
                        "version ${executor.minimumSupportedContractVersion}, which is below the first"
            }
            check(executor.contractVersion >= executor.minimumSupportedContractVersion) {
                "InformationRequestCapabilityExecutor $name serves $capability up to contract " +
                        "version ${executor.contractVersion} and from " +
                        "${executor.minimumSupportedContractVersion}, which is an empty range"
            }
            check(executor.contractVersion <= capability.contractVersion) {
                "InformationRequestCapabilityExecutor $name implements $capability at contract " +
                        "version ${executor.contractVersion}, above the declared " +
                        "${capability.contractVersion}"
            }
            indexed[capability] = executor
        }
        return indexed
    }
}
