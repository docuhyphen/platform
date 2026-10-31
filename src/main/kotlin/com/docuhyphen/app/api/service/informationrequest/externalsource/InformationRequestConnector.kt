package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorCall
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorContract
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorOutcome
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

interface InformationRequestConnector
{
    val contract: InformationRequestConnectorContract

    fun request(call: InformationRequestConnectorCall): InformationRequestConnectorOutcome

    fun poll(call: InformationRequestConnectorCall): InformationRequestConnectorOutcome
}

@ApplicationScoped
class InformationRequestConnectorRegistry
{
    @Inject
    lateinit var connectorInstances: Instance<InformationRequestConnector>

    private val testConnectors: List<InformationRequestConnector>?

    constructor()
    {
        testConnectors = null
    }

    internal constructor(connectors: List<InformationRequestConnector>)
    {
        testConnectors = connectors
    }

    fun find(key: String): InformationRequestConnector?
    {
        val matching = connectors().filter { it.contract.key == key }
        check(matching.size <= 1) { "More than one connector is installed under the key $key" }
        return matching.singleOrNull()
    }


    private fun connectors(): List<InformationRequestConnector> = testConnectors ?: connectorInstances.toList()
}
