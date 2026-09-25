package com.docuhyphen.app.api.config

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import java.time.Clock

@ApplicationScoped
class SystemClockProducer
{
    @Produces
    @Singleton
    fun clock(): Clock = Clock.systemUTC()
}
