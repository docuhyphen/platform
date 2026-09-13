package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef

/** The stored owner configuration and acting user after every mutation gate has allowed them. */
data class InformationRequestTemplateMutationContext(
    val definition: InformationRequestTemplateDefinition,
    val principal: PrincipalRef,
)
