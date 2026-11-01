package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException

object InformationRequestRespondentKey {
    private const val PURPOSE = "information-request-respondent"

    fun of(principal: PrincipalRef): String = CommandRequestFingerprint.of(PURPOSE, principal.kind, principal.id)

    fun requireHeldBy(respondentKey: String?, principal: PrincipalRef) {
        if (respondentKey == null || respondentKey == of(principal)) return
        throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.RESPONDENT_CHANGED,
            "These answers were entered by another respondent and were not saved",
        )
    }
}
