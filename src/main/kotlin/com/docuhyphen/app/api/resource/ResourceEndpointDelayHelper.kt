package com.docuhyphen.app.api.resource

import kotlin.random.Random

object ResourceEndpointDelayHelper
{
    fun delayEndpoint(n1: Long, n2: Long)
    {
        val delayTime = Random.nextLong(n1, n2)
        Thread.sleep(delayTime)
    }

    inline fun <T> withFixedFloor(floorMillis: Long, block: () -> T): T
    {
        val start = System.currentTimeMillis()
        val result = try
        {
            block()
        }
        finally
        {
            val elapsed = System.currentTimeMillis() - start
            val remaining = floorMillis - elapsed
            if (remaining > 0)
            {
                Thread.sleep(remaining)
            }
        }
        return result
    }
}