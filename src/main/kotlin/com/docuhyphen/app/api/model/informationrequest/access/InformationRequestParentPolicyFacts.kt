package com.docuhyphen.app.api.model.informationrequest.access

import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyFacts

data class InformationRequestParentPolicyFacts(val parent: InformationRequestParentSnapshot) : ResourcePolicyFacts
