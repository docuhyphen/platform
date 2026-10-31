package com.docuhyphen.app.api.repository.informationrequest.response

import com.docuhyphen.app.api.model.entity.InformationRequestResponseConditionEffect
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class InformationRequestResponseConditionEffectRepository :
    BaseRepository<InformationRequestResponseConditionEffect>(InformationRequestResponseConditionEffect::class.java)
