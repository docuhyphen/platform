package com.docuhyphen.app.api.service.command.model

internal data class OverlappingCommandOutcome<T>(
    val overlapped: Boolean,
    val results: List<Result<T>>,
)
