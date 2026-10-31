package com.docuhyphen.app.api.service.informationrequest.capability

import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapabilityRequirement

class InformationRequestCapabilityNotInstalledException(
    override val message: String,
    val unserved: List<InformationRequestCapabilityRequirement>,
) : RuntimeException(message)
