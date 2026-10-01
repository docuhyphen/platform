package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestSubjectDto
import com.docuhyphen.app.api.model.dto.InformationRequestSubjectReferenceDto
import com.docuhyphen.app.api.model.entity.SubjectIdentityExternalIdentifier
import com.docuhyphen.app.api.model.entity.SubjectIdentityRef

object InformationRequestSubjectDtoMapper
{
    fun toDto(
        subject: SubjectIdentityRef,
        identifiers: List<SubjectIdentityExternalIdentifier>
    ): InformationRequestSubjectDto =
        InformationRequestSubjectDto(
            id = subject.id,
            subjectKind = subject.subjectKind,
            references = identifiers.map { identifier ->
                InformationRequestSubjectReferenceDto(
                    authority = identifier.authority,
                    identifierType = identifier.identifierType,
                    identifierValue = identifier.identifierValue,
                )
            },
            createdAt = subject.createdAt,
        )
}
