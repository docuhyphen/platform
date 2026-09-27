package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnershipChange
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnershipChangeDecision
import com.docuhyphen.app.api.model.recordpreservation.RecordTransferVerdict
import io.quarkus.arc.DefaultBean
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty

interface RecordStorageLocationPolicy
{
    fun locationFor(owner: RecordOwnerRef, resourceType: String): String
}

interface RecordTransferPolicy
{
    fun decide(owner: RecordOwnerRef, region: String): RecordTransferVerdict
}

interface RecordOwnershipChangePolicy
{
    fun decide(change: RecordOwnershipChange): RecordOwnershipChangeDecision
}

@DefaultBean
@ApplicationScoped
class ConfiguredRecordStorageLocationPolicy @Inject constructor(
    @ConfigProperty(name = "app.records.storage-location", defaultValue = "primary")
    private val location: String,
) : RecordStorageLocationPolicy
{
    override fun locationFor(owner: RecordOwnerRef, resourceType: String): String = location.trim().ifBlank { "primary" }
}

@DefaultBean
@ApplicationScoped
class ConfiguredRecordTransferPolicy @Inject constructor(
    @ConfigProperty(name = "app.records.permitted-transfer-regions", defaultValue = "*")
    private val permittedRegions: String,
) : RecordTransferPolicy
{
    override fun decide(owner: RecordOwnerRef, region: String): RecordTransferVerdict
    {
        val permitted = permittedRegions.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val requested = region.trim().lowercase()
        return if (ANY in permitted || requested in permitted) RecordTransferVerdict.PERMITTED else RecordTransferVerdict.REFUSED
    }

    private companion object
    {
        const val ANY = "*"
    }
}

@DefaultBean
@ApplicationScoped
class ConfiguredRecordOwnershipChangePolicy @Inject constructor(
    @ConfigProperty(name = "app.records.member-removal-party-decision", defaultValue = "RETAIN")
    private val decision: String,
) : RecordOwnershipChangePolicy
{
    override fun decide(change: RecordOwnershipChange): RecordOwnershipChangeDecision =
        RecordOwnershipChangeDecision.entries.firstOrNull { it.name == decision.trim().uppercase() } ?: RecordOwnershipChangeDecision.RETAIN
}
