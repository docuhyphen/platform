package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyFacts
import com.docuhyphen.app.api.service.informationrequest.InformationRequestParentSnapshot

data class InformationRequestParentPolicyFacts(val parent: InformationRequestParentSnapshot) : ResourcePolicyFacts
