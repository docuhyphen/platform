package com.docuhyphen.app.api.service.informationrequest.disposal

import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalOutcome
import java.util.*

class InformationRequestDisposalRefusedException(
    val refusals: Map<UUID, InformationRequestDisposalOutcome.Refused>,
) : RuntimeException("Information Request disposal refused for ${refusals.keys.sorted().joinToString()}")
