package com.docuhyphen.app.api.repository.document

import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class DocumentRepository : BaseRepository<Document>(Document::class.java)
