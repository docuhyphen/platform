package com.docuhyphen.app.api.service.audit

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LedgerProcessorSchedulerTest
{
    @Test
    fun `repeated ticks drain only durable outstanding work`()
    {
        val processor = mock<LedgerProcessor>()
        whenever(processor.drain(200)).thenReturn(
            LedgerDrainResult(appended = 2, alreadyLedgered = 0, failed = 0),
            LedgerDrainResult(appended = 0, alreadyLedgered = 2, failed = 0),
        )
        val scheduler = LedgerProcessorScheduler(processor)

        scheduler.tick()
        scheduler.tick()

        verify(processor, times(2)).drain(200)
    }
}
