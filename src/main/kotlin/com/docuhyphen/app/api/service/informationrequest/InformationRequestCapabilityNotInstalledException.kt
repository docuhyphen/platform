package com.docuhyphen.app.api.service.informationrequest

/**
 * Thrown when a Template Version requires runtime capabilities this deployment does not serve.
 *
 * [unserved] names every requirement that could not be met, so the caller can report all of them
 * rather than the first. This is not a validation failure and not an authorization failure: the
 * Version is structurally valid and the caller may be entitled to use it, but the runtime it needs
 * is not installed in this deployment, and no author or respondent can act on that.
 */
class InformationRequestCapabilityNotInstalledException(
    override val message: String,
    val unserved: List<InformationRequestCapabilityRequirement>,
) : RuntimeException(message)
