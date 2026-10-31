package com.docuhyphen.app.api.service.informationrequest.record

import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestRecordExportView
import com.docuhyphen.app.api.repository.informationrequest.record.InformationRequestRecordExportRepository
import com.docuhyphen.app.api.service.command.OverlappingCommandRace
import com.docuhyphen.app.api.service.informationrequest.record.model.ExportAllowanceRaceOutcome
import org.mockito.AdditionalAnswers
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.*
import javax.sql.DataSource

internal class ExportAllowanceRace(
    private val dataSource: DataSource,
    private val exports: InformationRequestRecordExportRepository,
) {
    private val transactions = OverlappingCommandRace(dataSource)

    fun run(
        first: (InformationRequestRecordExportRepository) -> InformationRequestRecordExportView,
        second: (InformationRequestRecordExportRepository) -> InformationRequestRecordExportView,
    ): ExportAllowanceRaceOutcome {
        val firstCounted = CountDownLatch(1)
        val firstReleased = CountDownLatch(1)
        val secondCounted = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val firstAttempt = attempt(pool) { first(counting(firstCounted, firstReleased)) }
            check(
                counted(
                    firstCounted,
                    firstAttempt
                )
            ) { "The first export ended before it counted its owner's exports" }
            val secondAttempt = attempt(pool) { second(counting(secondCounted, null)) }
            val waited = waitedBehindFirst(secondCounted, secondAttempt)
            val countedWhileHeld = secondCounted.count == 0L
            firstReleased.countDown()
            return ExportAllowanceRaceOutcome(
                secondWaitedBehindFirst = waited,
                secondCountedWhileFirstHeld = countedWhileHeld,
                first = firstAttempt.get(60, TimeUnit.SECONDS),
                second = secondAttempt.get(60, TimeUnit.SECONDS),
            )
        } finally {
            firstReleased.countDown()
            pool.shutdownNow()
        }
    }

    private fun attempt(
        pool: ExecutorService,
        export: () -> InformationRequestRecordExportView,
    ): CompletableFuture<Result<InformationRequestRecordExportView>> =
        CompletableFuture.supplyAsync({ runCatching { transactions.inNewTransaction(export) } }, pool)

    private fun counting(counted: CountDownLatch, release: CountDownLatch?): InformationRequestRecordExportRepository {
        val counting =
            mock<InformationRequestRecordExportRepository>(defaultAnswer = AdditionalAnswers.delegatesTo(exports))
        doAnswer { call ->
            val window = exports.windowSince(call.getArgument(0), call.getArgument(1), call.getArgument(2))
            counted.countDown()
            release?.await(30, TimeUnit.SECONDS)
            window
        }.whenever(counting).windowSince(any(), any(), any())
        return counting
    }

    private fun counted(latch: CountDownLatch, attempt: CompletableFuture<*>): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline && !attempt.isDone) {
            if (latch.await(25, TimeUnit.MILLISECONDS)) return true
        }
        return latch.count == 0L
    }

    private fun waitedBehindFirst(secondCounted: CountDownLatch, secondAttempt: CompletableFuture<*>): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline && !secondAttempt.isDone && secondCounted.count > 0L) {
            if (advisoryLockWaiters() > 0) return true
            Thread.sleep(25)
        }
        return false
    }

    private fun advisoryLockWaiters(): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND wait_event = 'advisory'",
            )
        }
}
