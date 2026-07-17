package com.docuhyphen.app.api.exception

open class OrganizationTrustException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class OrganizationTrustNotFoundException(message: String = "Trusted Organization relationship not found") :
    OrganizationTrustException(message)

class OrganizationTrustValidationException(message: String) : OrganizationTrustException(message)

class OrganizationTrustAuthorizationException(message: String = "Not authorized for this Trusted Organization operation") :
    OrganizationTrustException(message)

open class OrganizationTrustConflictException(message: String, cause: Throwable? = null) :
    OrganizationTrustException(message, cause)

class OrganizationTrustStaleVersionException(message: String = "The Trusted Organization record was changed") :
    OrganizationTrustConflictException(message)

class OrganizationTrustRateLimitException(message: String = "Too many organization searches") :
    OrganizationTrustException(message)

class ExternalIdentityResolutionUnavailableException(
    message: String = "Trusted member could not be verified",
) : OrganizationTrustException(message)
