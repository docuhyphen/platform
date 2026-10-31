package com.docuhyphen.app.api.service.informationrequest.model

import com.docuhyphen.app.api.service.informationrequest.externalsource.InformationRequestConnector
import java.time.Clock

data class ExternalSourceSetup(val connectors: List<InformationRequestConnector>, val clock: Clock)
