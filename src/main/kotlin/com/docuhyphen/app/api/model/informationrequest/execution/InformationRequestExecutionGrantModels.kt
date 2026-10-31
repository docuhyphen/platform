package com.docuhyphen.app.api.model.informationrequest.execution

enum class InformationRequestEntitlementSource {
    PAID,
    TRIAL,
    OVERRIDE,
    UNENFORCED,
}

enum class InformationRequestContinuationAction {
    RESPOND,
    SUBMIT,
    ATTEST,
    REVIEW,
    ASSIGN_PARTY_WITHIN_CAP,
    UPLOAD_WITHIN_ALLOWANCE,
    NON_EXPANDING_AMENDMENT,
    SEND_REMINDER,
    CANCEL,
    SUPERSEDE,
}

enum class InformationRequestScheduledContinuation {
    PROCEEDS,
    OPERATIONALLY_SUSPENDED,
    OWNER_CHANGES_UNAVAILABLE,
    EXECUTION_GRANT_REVOKED,
    CONTINUATION_NOT_GRANTED,
    ;

    val proceeds: Boolean get() = this == PROCEEDS
}
