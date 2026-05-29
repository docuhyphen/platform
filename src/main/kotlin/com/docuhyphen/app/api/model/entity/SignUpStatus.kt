package com.docuhyphen.app.api.model.entity

/**
 * Lifecycle states for a sign-up record in [SignUpEntity].
 *
 * NB: must declare a package. When this file lived in the default (unnamed)
 * package, packaged classes that imported it via `import SignUpStatus`
 * intermittently triggered a Kotlin K2 compiler internal error
 * (`Source classes should be created separately before referencing`) during
 * the FIR → IR conversion,  `Fir2IrDeclarationStorage.findIrParent` cannot
 * resolve a parent IR module fragment for the unnamed package while a
 * consumer's companion-object is being processed.
 */
enum class SignUpStatus
{
    PENDING,
    VERIFIED,
    EXPIRED,
    EXPIRED_MAX_RETRIES,
    OTP_LOCKED
}