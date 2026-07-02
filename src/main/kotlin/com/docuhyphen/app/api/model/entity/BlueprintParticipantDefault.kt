package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.util.*

/**
 * One internal participant default attached to a [BlueprintDefinition]. Replaces the
 * `participants[]` array formerly embedded in `config_json`. The principal is polymorphic
 * (`principalKind` is the blueprint-level token APP_USER or PRINCIPAL_GROUP), so there is no
 * single FK target; `principalId` is stored as text to round-trip the value losslessly.
 */
@Entity
@Serializable
@Table(name = "blueprint_participant_default")
class BlueprintParticipantDefault
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "blueprint_definition_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var blueprintDefinitionId: UUID

    @Column(name = "principal_kind", nullable = false, length = 32)
    lateinit var principalKind: String

    @Column(name = "principal_id", nullable = false, length = 64)
    lateinit var principalId: String

    @Column(name = "role_name", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    var roleName: ExchangeShareRoleName = ExchangeShareRoleName.VIEWER

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0

    constructor()
}
