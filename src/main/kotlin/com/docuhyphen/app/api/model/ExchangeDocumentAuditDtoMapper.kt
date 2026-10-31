package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.DocumentAuditDetailedDto
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.model.entity.Document
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object ExchangeDocumentAuditDtoMapper
{
    private val payloadSerializer = MapSerializer(String.serializer(), String.serializer())

    fun toDto(event: AuditLedgerEvent, documentsById: Map<String, Document>, action: String): DocumentAuditDetailedDto
    {
        val payload = runCatching { Json.decodeFromString(payloadSerializer, event.payloadJson) }
            .getOrDefault(emptyMap())
        val documentId = event.targetId
        val document = documentId?.let { documentsById[it] }

        return DocumentAuditDetailedDto(
            id = event.id,
            timestamp = event.occurredAt,
            action = action,
            performedBy = null,
            performedByEmail = payload["actor_email"],
            documentId = documentId,
            documentTitle = document?.title ?: payload["document_title"],
        )
    }
}
