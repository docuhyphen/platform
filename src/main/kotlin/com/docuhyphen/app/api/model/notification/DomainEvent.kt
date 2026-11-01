@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.docuhyphen.app.api.model.notification

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.*

@Serializable
data class DomainEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val occurredAtEpochMillis: Long = System.currentTimeMillis(),
    val actor: PrincipalRefDto? = null,
    val subject: SubjectRef? = null,
    val organizationId: String? = null,
    val payload: Map<String, String> = emptyMap(),
    val orderingKey: String? = null,
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

object DomainEventJson
{
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }
}

