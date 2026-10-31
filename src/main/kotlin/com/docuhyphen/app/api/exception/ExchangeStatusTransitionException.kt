package com.docuhyphen.app.api.exception

import com.docuhyphen.app.api.model.entity.ExchangeStatus

class ExchangeStatusTransitionException(
    val fromStatus: ExchangeStatus,
    val toStatus: ExchangeStatus,
) : RuntimeException("This Exchange is ${fromStatus.name} and cannot change to ${toStatus.name}")
