package com.dochyphen.app.api.resource

import kotlin.random.Random

object ResourceEndpointDelayHelper
{
    fun delayEndpoint(n1: Long, n2: Long)
    {
        val delayTime = Random.nextLong(n1, n2)
        Thread.sleep(delayTime)
    }
}