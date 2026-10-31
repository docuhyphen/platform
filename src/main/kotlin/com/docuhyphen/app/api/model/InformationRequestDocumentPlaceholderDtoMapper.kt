package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestDocumentPlaceholderDto
import com.docuhyphen.app.api.model.entity.InformationRequestDocumentPlaceholder

object InformationRequestDocumentPlaceholderDtoMapper {
    fun toDto(placeholder: InformationRequestDocumentPlaceholder): InformationRequestDocumentPlaceholderDto =
        InformationRequestDocumentPlaceholderDto(
            id = placeholder.id,
            title = placeholder.title,
            required = placeholder.required,
            restrictType = placeholder.restrictType,
            restrictedType = placeholder.restrictedType,
            libraryDocumentId = placeholder.libraryDocumentId,
            libraryTitle = placeholder.libraryTitle,
            libraryDescription = placeholder.libraryDescription,
            libraryDocumentType = placeholder.libraryDocumentType,
            libraryFileName = placeholder.libraryFileName,
            libraryFileSizeBytes = placeholder.libraryFileSizeBytes,
            displayOrder = placeholder.displayOrder,
        )
}
