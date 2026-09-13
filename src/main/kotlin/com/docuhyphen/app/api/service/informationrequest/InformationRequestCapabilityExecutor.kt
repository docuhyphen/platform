package com.docuhyphen.app.api.service.informationrequest

/**
 * A runtime implementation of one [InformationRequestCapability], installed as a CDI bean.
 *
 * Installing an executor is how a deployment states that it can serve a capability. Nothing else
 * does: a Template Version may freeze while the executor it needs is still being built, and a
 * request cannot be created against that Version until the executor is there.
 *
 * An executor serves a range of contract versions rather than one, so raising a capability's
 * contract version does not strand the Versions already published against the older one. The range
 * runs from [minimumSupportedContractVersion] to [contractVersion] inclusive.
 */
interface InformationRequestCapabilityExecutor
{
    val capability: InformationRequestCapability

    /** The newest contract version of [capability] this executor implements. */
    val contractVersion: Int

    /**
     * The oldest contract version of [capability] this executor still honours. Equal to
     * [contractVersion] for an executor that serves only the newest contract.
     */
    val minimumSupportedContractVersion: Int
}

/**
 * What an installation can serve, indexed by the capability each executor serves.
 *
 * Construction is where an incoherent installation is rejected, because none of these leaves a
 * working deployment: two executors claiming one capability leave no answer to which serves it, and
 * an impossible or unrecognized contract range leaves a requirement that can neither be met nor
 * diagnosed.
 */
class InstalledCapabilities(executors: Iterable<InformationRequestCapabilityExecutor>)
{
    private val index: Map<InformationRequestCapability, InformationRequestCapabilityExecutor> =
        buildIndex(executors)

    fun installedCapabilities(): Set<InformationRequestCapability> = index.keys

    /** Whether an installed executor honours the contract version [requirement] names. */
    fun serves(requirement: InformationRequestCapabilityRequirement): Boolean
    {
        val executor = index[requirement.capability] ?: return false
        return requirement.requiredContractVersion >= executor.minimumSupportedContractVersion &&
            requirement.requiredContractVersion <= executor.contractVersion
    }

    /**
     * The requirements this installation cannot meet, in the order given. Empty means every
     * requirement is served, which is what lets a request be created against the Version holding
     * them.
     */
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
            // An executor cannot implement a contract the platform has not defined, so a version
            // above the declared one is a stale executor rather than a newer capability.
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
