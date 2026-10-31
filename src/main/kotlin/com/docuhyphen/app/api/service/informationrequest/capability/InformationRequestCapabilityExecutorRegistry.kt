package com.docuhyphen.app.api.service.informationrequest.capability

import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapabilityRequirement
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

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

    fun unserved(
        requirements: Iterable<InformationRequestCapabilityRequirement>,
    ): List<InformationRequestCapabilityRequirement> = installed.unserved(requirements)
}
