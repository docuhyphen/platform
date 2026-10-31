package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.service.command.RevisionETag

object InformationRequestTemplateETag {
    fun draftOf(draft: InformationRequestTemplateVersion): String = RevisionETag.of(draft.id, draft.revision)
}
