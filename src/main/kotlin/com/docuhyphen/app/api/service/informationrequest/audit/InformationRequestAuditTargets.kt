package com.docuhyphen.app.api.service.informationrequest.audit

object InformationRequestAuditTargets {
    const val REQUEST = "INFORMATION_REQUEST"
    const val TEMPLATE_DEFINITION = "INFORMATION_REQUEST_TEMPLATE_DEFINITION"
    const val CLOCK_POLICY = "INFORMATION_REQUEST_CLOCK_POLICY"
    const val PRIVACY_REQUEST = "INFORMATION_REQUEST_PRIVACY_REQUEST"

    val OWNER_SEARCH: Set<String> = setOf(REQUEST, TEMPLATE_DEFINITION, CLOCK_POLICY, PRIVACY_REQUEST)
}
