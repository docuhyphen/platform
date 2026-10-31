package com.docuhyphen.app.api.service.informationrequest.capability.model

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability

data class InformationRequestTemplateWalkingSkeletonFixture(
    val fixtureKey: String,
    val templateKey: String,
    val fixtureContractVersion: Int,
    val configuration: InformationRequestTemplateConfigurationRequest,
    val expectedRequirementKeys: List<String>,
    val expectedCapabilities: List<InformationRequestCapability>,
)
