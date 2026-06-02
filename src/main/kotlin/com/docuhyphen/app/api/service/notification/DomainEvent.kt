@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Canonical domain event envelope. Published by [DomainEventPublisher] from any service
 * after a meaningful state change. Both the in-process router and (future) Kafka consumer
 * will deserialise into this shape.
 *
 * Field design notes:
 *   * `id` is a fresh UUID per event so delivery logs can correlate fanouts.
 *   * `type` follows the namespaced taxonomy (`session.*`, `share.*`, `workflow.*`, …).
 *   * `actor` is who *caused* the event. May be null for system-triggered events.
 *   * `subject` is what the event is *about* (a session, a document, a group).
 *   * `payload` is a free-form string map. Plain strings keep wire-format compatibility
 *     with the kotlinx-serialized default; richer payloads stash UUIDs/json-strings here.
 */
@Serializable
data class DomainEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val occurredAtEpochMillis: Long = System.currentTimeMillis(),
    val actor: PrincipalRefDto? = null,
    val subject: SubjectRef? = null,
    val organizationId: String? = null,
    val payload: Map<String, String> = emptyMap(),
)
{
    @Serializable
    data class PrincipalRefDto(val kind: String, val id: String)
    {
        fun toPrincipalRef(): PrincipalRef? = runCatching {
            PrincipalRef(PrincipalKind.valueOf(kind), UUID.fromString(id))
        }.getOrNull()

        companion object
        {
            fun from(ref: PrincipalRef) = PrincipalRefDto(ref.kind.name, ref.id.toString())
        }
    }

    @Serializable
    data class SubjectRef(val type: String, val id: String)

    fun eventUuid(): UUID = UUID.fromString(id)
}

/**
 * Single shared JSON instance for event serialization. Matches the project's existing
 * kotlinx-serialization style (ignore-unknown + class discriminator). Reused by the
 * Kafka emitter when it's wired in a later iteration.
 */
object DomainEventJson
{
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }
}

