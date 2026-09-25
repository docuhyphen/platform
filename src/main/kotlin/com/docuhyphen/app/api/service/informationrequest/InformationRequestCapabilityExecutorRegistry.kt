package com.docuhyphen.app.api.service.informationrequest

import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

/**
 * Discovers every installed [InformationRequestCapabilityExecutor] at startup and answers what this
 * deployment can serve. An incoherent set of executors fails startup rather than being narrowed,
 * because a deployment that cannot say which executor serves a capability cannot serve it safely.
 */
@ApplicationScoped
class InformationRequestCapabilityExecutorRegistry
{
    @Inject
    @Any
    private lateinit var executors: Instance<InformationRequestCapabilityExecutor>

    private lateinit var installed: InstalledCapabilities

    @PostConstruct
    fun init()
    {
        installed = InstalledCapabilities(executors)
    }

    fun installedCapabilities(): Set<InformationRequestCapability> = installed.installedCapabilities()

    fun serves(requirement: InformationRequestCapabilityRequirement): Boolean = installed.serves(requirement)

    fun unserved(
        requirements: Iterable<InformationRequestCapabilityRequirement>,
    ): List<InformationRequestCapabilityRequirement> = installed.unserved(requirements)
}
