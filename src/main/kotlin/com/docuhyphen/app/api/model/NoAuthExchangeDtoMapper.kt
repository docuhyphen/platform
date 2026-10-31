package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.NoAuthExchangeBasicDto
import com.docuhyphen.app.api.model.entity.Exchange

object NoAuthExchangeDtoMapper
{
    fun toDto(
        exchange: Exchange,
        allowDocumentDownload: Boolean,
        accessVerificationRequired: Boolean,
    ): NoAuthExchangeBasicDto? =
        BasicEntityToDtoTransformer.toNoAuthDto(exchange)?.apply {
            this.allowDocumentDownload = allowDocumentDownload
            this.accessVerificationRequired = accessVerificationRequired
        }
}
