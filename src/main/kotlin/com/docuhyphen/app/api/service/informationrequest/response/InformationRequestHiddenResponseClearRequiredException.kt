package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.informationrequest.response.HiddenResponseClearTarget
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException

class InformationRequestHiddenResponseClearRequiredException(
    val targets: List<HiddenResponseClearTarget>,
) : InformationRequestLifecycleException(
    InformationRequestErrorCatalog.HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED,
    "Hidden response data requires explicit clearing confirmation",
)
