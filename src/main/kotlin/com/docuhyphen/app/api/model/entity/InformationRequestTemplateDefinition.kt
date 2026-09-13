package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * The stable identity of one reusable request for information, and the owner that holds it. The
 * configuration itself lives in an [InformationRequestTemplateVersion]; this record is what an
 * author keeps editing and what a later Version is a new answer to.
 */
@Entity
@Table(name = "information_request_template_definition")
class InformationRequestTemplateDefinition
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "scope_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var scopeKind: InformationRequestTemplateScopeKind = InformationRequestTemplateScopeKind.ORGANIZATION

    /** Set when [scopeKind] is ORGANIZATION; the one organization that owns this definition. */
    @Column(name = "scope_org_id", nullable = true)
    var scopeOrgId: UUID? = null

    /** Set when [scopeKind] is PERSONAL; the one user that owns this definition. */
    @Column(name = "scope_user_id", nullable = true)
    var scopeUserId: UUID? = null

    @Column(name = "namespace", nullable = false, length = 128)
    lateinit var namespace: String

    @Column(name = "template_key", nullable = false, length = 128)
    lateinit var templateKey: String

    @Column(name = "display_name", nullable = false, length = 255)
    lateinit var displayName: String

    @Column(name = "description", nullable = true, length = 1024)
    var description: String? = null

    @Column(name = "status", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var status: InformationRequestTemplateStatus = InformationRequestTemplateStatus.DRAFT

    @Column(name = "origin_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var originKind: InformationRequestTemplateOriginKind = InformationRequestTemplateOriginKind.REUSABLE

    @Column(name = "origin_request_id", nullable = true)
    var originRequestId: UUID? = null

    @Column(name = "created_by_app_user_id", nullable = true)
    var createdByAppUserId: UUID? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
