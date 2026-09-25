package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceVersion
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class InformationRequestEvidenceCoverage(
    val startsOn: LocalDate,
    val endsOn: LocalDate,
)
{
    init
    {
        require(!endsOn.isBefore(startsOn)) { "An evidence coverage period ends on or after it starts" }
    }

    fun days(): Long = ChronoUnit.DAYS.between(startsOn, endsOn) + 1
}

data class InformationRequestEvidenceAttributes(
    val issuer: String? = null,
    val jurisdiction: String? = null,
    val language: String? = null,
    val issuedOn: LocalDate? = null,
    val expiresOn: LocalDate? = null,
    val coverage: InformationRequestEvidenceCoverage? = null,
    val certificationReference: String? = null,
    val signatureReference: String? = null,
)
{
    init
    {
        requireStated("issuer", issuer, TEXT_LIMIT)
        requireStated("jurisdiction", jurisdiction, JURISDICTION_LIMIT)
        requireStated("language", language, LANGUAGE_LIMIT)
        requireStated("certification reference", certificationReference, TEXT_LIMIT)
        requireStated("signature reference", signatureReference, TEXT_LIMIT)
        require(issuedOn == null || expiresOn == null || !expiresOn.isBefore(issuedOn))
        {
            "An evidence expiry date falls on or after its issue date"
        }
    }

    companion object
    {
        val NONE = InformationRequestEvidenceAttributes()

        private const val TEXT_LIMIT = 255
        private const val JURISDICTION_LIMIT = 64
        private const val LANGUAGE_LIMIT = 35

        private fun requireStated(name: String, value: String?, limit: Int)
        {
            require(value == null || value.isNotBlank()) { "An evidence $name must not be blank" }
            require(value == null || value.length <= limit) { "An evidence $name is at most $limit characters" }
        }
    }
}

object InformationRequestEvidenceAttributesMapper
{
    fun read(version: InformationRequestEvidenceVersion): InformationRequestEvidenceAttributes =
        InformationRequestEvidenceAttributes(
            issuer = version.issuer,
            jurisdiction = version.jurisdiction,
            language = version.language,
            issuedOn = version.issuedOn,
            expiresOn = version.expiresOn,
            coverage = version.coverageStartsOn?.let { startsOn ->
                InformationRequestEvidenceCoverage(
                    startsOn,
                    requireNotNull(version.coverageEndsOn) { "A recorded coverage period states both ends" },
                )
            },
            certificationReference = version.certificationReference,
            signatureReference = version.signatureReference,
        )

    fun write(version: InformationRequestEvidenceVersion, attributes: InformationRequestEvidenceAttributes)
    {
        version.issuer = attributes.issuer
        version.jurisdiction = attributes.jurisdiction
        version.language = attributes.language
        version.issuedOn = attributes.issuedOn
        version.expiresOn = attributes.expiresOn
        version.coverageStartsOn = attributes.coverage?.startsOn
        version.coverageEndsOn = attributes.coverage?.endsOn
        version.certificationReference = attributes.certificationReference
        version.signatureReference = attributes.signatureReference
    }
}
