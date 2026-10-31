package com.docuhyphen.app.api.service.informationrequest.record

import kotlinx.serialization.json.*
import java.util.*

object InformationRequestRecordContent {
    fun disclosedRequirementIds(content: String): Set<UUID>? {
        val root = runCatching { Json.parseToJsonElement(content) as? JsonObject }.getOrNull() ?: return null
        val records = (root["requests"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: listOf(root)
        return records.flatMap(::disclosedIn).toSet()
    }

    private fun disclosedIn(record: JsonObject): List<UUID> = buildList {
        objectsAt(record, "packages").forEach { submission -> addAll(disclosed(objectsAt(submission, "items"))) }
        addAll(disclosed(objectsAt(record, "itemCorrections")))
        (record["externalSources"] as? JsonObject)?.let { sources ->
            addAll(disclosed(objectsAt(sources, "importedValues")))
            addAll(disclosed(objectsAt(sources, "connectorExchanges")))
        }
    }

    private fun disclosed(entries: List<JsonObject>): List<UUID> =
        entries
            .filter { (it["redacted"] as? JsonPrimitive)?.booleanOrNull != true }
            .mapNotNull { (it["requirementId"] as? JsonPrimitive)?.contentOrNull?.let(::uuidOrNull) }

    private fun objectsAt(container: JsonObject, key: String): List<JsonObject> =
        (container[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun uuidOrNull(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()
}
