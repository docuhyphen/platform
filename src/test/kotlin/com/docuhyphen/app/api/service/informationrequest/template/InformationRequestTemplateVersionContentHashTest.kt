package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestTemplateVersionContentHashTest {
    private val schemaVersionId = UUID.randomUUID()
    private val fieldId = UUID.randomUUID()

    @Test
    fun `the versioned hash document preserves the existing canonical bytes and ignores later DTO fields`() {
        val json =
            kotlinx.serialization.json.Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = true }
        val request = com.docuhyphen.app.api.model.InformationRequestTemplateConfigurationMapper.toRequest(version())
        val current = json.encodeToJsonElement(
            com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest.serializer(), request
        )
        val extended = kotlinx.serialization.json.JsonObject(
            (current as kotlinx.serialization.json.JsonObject) +
                    ("laterDtoMetadata" to kotlinx.serialization.json.JsonPrimitive("metadata"))
        )
        val frozen = json.decodeFromJsonElement(
            com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateContentHashV1.serializer(),
            extended
        )

        assertEquals(
            json.encodeToString(
                com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest.serializer(), request
            ),
            json.encodeToString(
                com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateContentHashV1.serializer(),
                frozen
            )
        )
    }

    @Test
    fun `unordered group retrieval does not change a Version content hash`() {
        val base = version()
        val group = base.groups.single()
        val configured =
            base.copy(groups = listOf(group.copy(groupKey = "second-entry"), group.copy(groupKey = "first-entry")))
        assertEquals(
            InformationRequestTemplateVersionContentHash.of(configured),
            InformationRequestTemplateVersionContentHash.of(configured.copy(groups = configured.groups.reversed()))
        )
    }

    @Test
    fun `a Version's content hash is a stable SHA-256 of what it configures`() {
        val version = version()

        val hash = InformationRequestTemplateVersionContentHash.of(version)

        assertTrue(hash.matches(Regex("^[0-9a-f]{64}$")), hash)
        assertEquals(hash, InformationRequestTemplateVersionContentHash.of(version()))
    }

    @Test
    fun `the identities, number, state, and dates of stored rows leave the hash unchanged`() {
        val base = version()
        val restated = base.copy(
            id = UUID.randomUUID(),
            templateDefinitionId = UUID.randomUUID(),
            versionNumber = 7,
            status = InformationRequestTemplateStatus.RETIRED,
            sections = base.sections.map { section ->
                section.copy(
                    id = UUID.randomUUID(),
                    requirements = section.requirements.map {
                        it.copy(id = UUID.randomUUID(), templateRequirementId = UUID.randomUUID())
                    },
                )
            },
            groups = base.groups.map { it.copy(id = UUID.randomUUID()) },
            publishedAt = Timestamp.from(Instant.parse("2026-10-01T00:00:00Z")),
            retiredAt = Timestamp.from(Instant.parse("2026-10-02T00:00:00Z")),
            createdAt = Timestamp.from(Instant.parse("2026-09-01T00:00:00Z")),
        )

        assertEquals(
            InformationRequestTemplateVersionContentHash.of(base),
            InformationRequestTemplateVersionContentHash.of(restated)
        )
    }

    @Test
    fun `any configured change gives a different hash`() {
        val base = version()
        val changed = listOf(
            base.copy(sections = base.sections.map { section ->
                section.copy(requirements = section.requirements.map { it.copy(prompt = "Provide the updated record") })
            }),
            base.copy(groups = base.groups.map { it.copy(maxOccurrences = 4) }),
            base.copy(submissionMode = InformationRequestSubmissionMode.STAGED),
            base.copy(schemaVersionId = UUID.randomUUID()),
            base.copy(sections = base.sections.map { it.copy(title = "Other records") }),
        )

        val hashes = (listOf(base) + changed).map(InformationRequestTemplateVersionContentHash::of)

        assertEquals(hashes.size, hashes.toSet().size, hashes.toString())
        changed.forEach {
            assertNotEquals(
                InformationRequestTemplateVersionContentHash.of(base),
                InformationRequestTemplateVersionContentHash.of(it)
            )
        }
    }

    private fun version() = InformationRequestTemplateVersionDto(
        id = UUID.randomUUID(),
        templateDefinitionId = UUID.randomUUID(),
        versionNumber = 2,
        status = InformationRequestTemplateStatus.PUBLISHED,
        schemaVersionId = schemaVersionId,
        sections = listOf(
            InformationRequestTemplateSectionDto(
                id = UUID.randomUUID(),
                sectionKey = "collected-records",
                title = "Collected records",
                requirements = listOf(
                    InformationRequestTemplateRequirementDto(
                        id = UUID.randomUUID(),
                        templateRequirementId = UUID.randomUUID(),
                        requirementKey = "recorded-note",
                        requirementType = InformationRequestRequirementType.FIELD,
                        prompt = "Provide the recorded note",
                        responseMode = InformationRequestResponseMode.PROVIDE,
                        requiredness = InformationRequestRequiredness.REQUIRED,
                        contributorRole = InformationRequestContributorRole.CONTRIBUTOR,
                        reviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED,
                        occurrenceAnchorKey = "entry",
                        collectedFieldDefinitionId = fieldId,
                        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                    ),
                ),
            ),
        ),
        groups = listOf(
            InformationRequestTemplateGroupDto(
                id = UUID.randomUUID(),
                groupKey = "entry",
                minOccurrences = 1,
                maxOccurrences = 3,
            ),
        ),
        createdAt = Timestamp.from(Instant.parse("2026-09-27T00:00:00Z")),
    )
}
