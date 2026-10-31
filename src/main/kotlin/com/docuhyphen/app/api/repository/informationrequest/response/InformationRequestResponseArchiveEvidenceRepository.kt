package com.docuhyphen.app.api.repository.informationrequest.response

import com.docuhyphen.app.api.model.entity.InformationRequestResponseArchiveEvidence
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class InformationRequestResponseArchiveEvidenceRepository :
    BaseRepository<InformationRequestResponseArchiveEvidence>(InformationRequestResponseArchiveEvidence::class.java)
