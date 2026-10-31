package com.docuhyphen.app.api.service.informationrequest.record

import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestRecordRelatedRequestProjection
import kotlinx.serialization.json.*
import java.util.*

object InformationRequestRecordRelatedRequests {
    private const val REQUESTS = "requests"
    private const val REQUEST = "request"
    private const val REPLACEMENT = "supersededByRequestId"
    private const val REPLACEMENT_REDACTED = "supersededByRequestRedacted"
    private const val RECERTIFICATIONS = "recertifications"
    private const val FACT_SOURCE = "sourceInformationRequestId"
    private const val FACT_SOURCE_REDACTED = "sourceRedacted"
    private val FACT_SOURCE_RECORDS = listOf(
        FACT_SOURCE,
        "sourcePackageId",
        "sourceSubmissionItemId",
        "sourceRequirementId",
        "sourceFieldValueRevisionId",
        "sourceReviewId",
        "evidenceVersionIds",
    )

    fun withheld(content: JsonObject, shown: (UUID) -> Boolean): InformationRequestRecordRelatedRequestProjection {
        val records = content[REQUESTS] as? JsonArray ?: return recordWithheld(content, shown)
        val projected = records.map { entry -> (entry as? JsonObject)?.let { recordWithheld(it, shown) } }
        val withheldCount = projected.sumOf { it?.withheldCount ?: 0 }
        if (withheldCount == 0) return InformationRequestRecordRelatedRequestProjection(content, 0)
        val requests = JsonArray(records.mapIndexed { index, entry -> projected[index]?.content ?: entry })
        return InformationRequestRecordRelatedRequestProjection(
            JsonObject(content + (REQUESTS to requests)),
            withheldCount
        )
    }

    private fun recordWithheld(
        record: JsonObject,
        shown: (UUID) -> Boolean
    ): InformationRequestRecordRelatedRequestProjection {
        val replaced = mutableMapOf<String, JsonElement>()
        var withheldCount = 0
        val request = record[REQUEST] as? JsonObject
        if (request != null && !disclosable(request[REPLACEMENT], shown)) {
            replaced[REQUEST] =
                JsonObject(request + mapOf(REPLACEMENT to JsonNull, REPLACEMENT_REDACTED to JsonPrimitive(true)))
            withheldCount++
        }
        val recertifications = record[RECERTIFICATIONS] as? JsonArray
        val hiddenSources = recertifications.orEmpty().map { it is JsonObject && !disclosable(it[FACT_SOURCE], shown) }
        if (recertifications != null && hiddenSources.any { it }) {
            replaced[RECERTIFICATIONS] = JsonArray(
                recertifications.mapIndexed { index, entry ->
                    if (hiddenSources[index]) withoutFactSource(entry.jsonObject) else entry
                },
            )
            withheldCount += hiddenSources.count { it }
        }
        if (withheldCount == 0) return InformationRequestRecordRelatedRequestProjection(record, 0)
        return InformationRequestRecordRelatedRequestProjection(JsonObject(record + replaced), withheldCount)
    }

    private fun disclosable(reference: JsonElement?, shown: (UUID) -> Boolean): Boolean = when (reference) {
        null, JsonNull -> true
        is JsonPrimitive -> reference.isString && uuidOrNull(reference.content)?.let(shown) == true
        else -> false
    }

    private fun withoutFactSource(recertification: JsonObject): JsonObject =
        JsonObject(
            recertification + FACT_SOURCE_RECORDS.associateWith { JsonNull } + (FACT_SOURCE_REDACTED to JsonPrimitive(
                true
            )),
        )

    private fun uuidOrNull(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()
}
