package com.docuhyphen.app.api.model.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef

data class InformationRequestTemplateMutationContext(
    val definition: InformationRequestTemplateDefinition,
    val principal: PrincipalRef,
)
