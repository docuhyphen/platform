package com.docuhyphen.app.api.service.informationrequest

/**
 * A runtime behavior a Template Version may need somebody to supply.
 *
 * Configuration and the runtime that executes it are built and released separately, so a Version
 * can freeze while part of what it asks for is still being implemented. Each value here names one
 * such behavior, and a Version records the ones its configuration needs.
 *
 * [contractVersion] is the current version of that behavior's contract. A Version records the
 * contract version it froze against, and an installed
 * [InformationRequestCapabilityExecutor] states which contract versions it can serve. Raise a
 * contract version when the behavior expected of an executor changes in a way an older executor
 * cannot honour; a Version published against the older contract keeps requiring the older one.
 *
 * Which capabilities a Version needs is derived from what it configures, not chosen, so this
 * vocabulary is closed and matched exactly by the capability keys the Version records.
 */
enum class InformationRequestCapability(val contractVersion: Int)
{
    /** Collecting a typed answer that resolves against a Schema Version. */
    STRUCTURED_RESPONSE(1),

    /** Collecting files against a stated evidence policy. */
    DOCUMENT_EVIDENCE(1),

    /** Collecting an assertion made by the responding party. */
    RESPONSE_ATTESTATION(1),

    /** Resolving whether a requirement applies from the rule the binding names. */
    CONDITIONAL_REQUIREMENT(1),

    /** Answering one requirement once per occurrence of the group it is anchored to. */
    REPEATABLE_OCCURRENCE(1),

    /** Recording a reviewer disposition before a requirement counts as satisfied. */
    RESPONSE_REVIEW(1),

    /** Narrowing who may read and write a response to the compartment it falls into. */
    CONFIDENTIALITY_COMPARTMENT(1),

    /** Resolving a requirement without the evidence it asks for, under the stated waiver rule. */
    EVIDENCE_WAIVER(1),

    /** Accepting one requested document in place of another. */
    SUBSTITUTE_EVIDENCE(1),

    /** Attaching a requested document to an answer given elsewhere as support for it. */
    SUPPORTING_EVIDENCE(1),

    /** Turning collected answers into an immutable submission. */
    RESPONSE_SUBMISSION(1);

    companion object
    {
        fun fromCodeOrNull(value: String?): InformationRequestCapability?
        {
            val normalized = value?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

/** One capability a Template Version needs, at the contract version it was frozen against. */
data class InformationRequestCapabilityRequirement(
    val capability: InformationRequestCapability,
    val requiredContractVersion: Int,
)
