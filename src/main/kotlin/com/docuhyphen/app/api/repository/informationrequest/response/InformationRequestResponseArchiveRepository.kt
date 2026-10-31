package com.docuhyphen.app.api.repository.informationrequest.response

import com.docuhyphen.app.api.model.entity.InformationRequestResponseArchive
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class InformationRequestResponseArchiveRepository :
    BaseRepository<InformationRequestResponseArchive>(InformationRequestResponseArchive::class.java)
