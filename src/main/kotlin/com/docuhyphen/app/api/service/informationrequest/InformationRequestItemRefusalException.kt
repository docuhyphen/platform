package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemProblem
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException

class InformationRequestItemRefusalException(
    reasonCode: String,
    message: String,
    val problems: List<InformationRequestItemProblem>,
    val undisclosedProblemCount: Int = 0,
) : InformationRequestLifecycleException(reasonCode, message)
