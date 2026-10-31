package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.ResolvedSchemaViewDto
import com.docuhyphen.app.api.model.dto.SchemaDefinitionDto
import com.docuhyphen.app.api.model.dto.SchemaFieldBindingDto
import com.docuhyphen.app.api.model.dto.SchemaVersionDto
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.entity.SchemaVersion

object SchemaDefinitionDtoMapper {
    fun toDto(
        definition: SchemaDefinition,
        draftVersion: SchemaVersionDto?,
        latestPublishedVersion: SchemaVersionDto?,
    ): SchemaDefinitionDto = SchemaDefinitionDto(
        id = definition.id,
        scopeKind = definition.scopeKind,
        scopeOrgId = definition.scopeOrgId,
        namespace = definition.namespace,
        schemaKey = definition.schemaKey,
        displayName = definition.displayName,
        description = definition.description,
        targetResourceType = definition.targetResourceType,
        status = definition.status,
        draftVersion = draftVersion,
        latestPublishedVersion = latestPublishedVersion,
        createdAt = definition.createdAt,
    )

    fun toVersionDto(version: SchemaVersion, bindings: List<SchemaFieldBindingDto>): SchemaVersionDto =
        SchemaVersionDto(
            id = version.id,
            schemaDefinitionId = version.schemaDefinitionId,
            versionNumber = version.versionNumber,
            status = version.status,
            compatibility = version.compatibility,
            bindings = bindings,
            publishedAt = version.publishedAt,
            createdAt = version.createdAt,
        )

    fun toResolvedViewDto(
        definition: SchemaDefinition,
        version: SchemaVersion,
        fields: List<SchemaFieldBindingDto>,
    ): ResolvedSchemaViewDto = ResolvedSchemaViewDto(
        schemaDefinitionId = definition.id,
        schemaKey = definition.schemaKey,
        namespace = definition.namespace,
        displayName = definition.displayName,
        schemaVersionId = version.id,
        versionNumber = version.versionNumber,
        targetResourceType = definition.targetResourceType,
        scopeKind = definition.scopeKind,
        fields = fields,
    )
}
