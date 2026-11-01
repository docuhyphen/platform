package com.docuhyphen.app.api.model.exchange

import com.docuhyphen.app.api.model.dto.ExchangeBasicDto
import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val content: Array<ExchangeBasicDto?>,
    val totalElements: Long,
    val totalPages: Int,
    val currentPage: Int,
    val pageSize: Int
)
