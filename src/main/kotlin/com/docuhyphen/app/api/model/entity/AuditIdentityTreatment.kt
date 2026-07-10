package com.docuhyphen.app.api.model.entity

/** Per-audience readability treatment for identity-bearing fields on a retained audit event. */
enum class AuditIdentityTreatment
{
    READABLE,
    MASKED,
    PSEUDONYMIZED,
    PROHIBITED,
}
