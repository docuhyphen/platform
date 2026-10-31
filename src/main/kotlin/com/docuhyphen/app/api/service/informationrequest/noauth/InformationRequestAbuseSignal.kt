package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseControl
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class InformationRequestAbuseSignal {
    fun refused(control: InformationRequestAbuseControl, detail: String = "") {
        InformationRequestAbuseLog.refused(control, detail)
    }
}
