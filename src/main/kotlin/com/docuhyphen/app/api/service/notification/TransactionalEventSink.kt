package com.docuhyphen.app.api.service.notification

import jakarta.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
)
annotation class TransactionalEventSink
