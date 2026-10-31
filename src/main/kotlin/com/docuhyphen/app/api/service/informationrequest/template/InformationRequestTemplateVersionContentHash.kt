package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.InformationRequestTemplateConfigurationMapper
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateContentHashV1
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object InformationRequestTemplateVersionContentHash {
    private val canonical = Json {
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    fun of(version: InformationRequestTemplateVersionDto): String {
        val configuration = canonical.encodeToJsonElement(
            InformationRequestTemplateConfigurationRequest.serializer(),
            InformationRequestTemplateConfigurationMapper.toRequest(
                version.copy(
                    groups = version.groups.sortedBy { it.groupKey },
                    conditionRules = version.conditionRules.sortedBy { it.ruleKey },
                )
            ),
        )
        val frozen =
            canonical.decodeFromJsonElement(InformationRequestTemplateContentHashV1.serializer(), configuration)
        val document = canonical.encodeToString(InformationRequestTemplateContentHashV1.serializer(), frozen)
        return MessageDigest.getInstance("SHA-256")
            .digest(document.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
    }
}
