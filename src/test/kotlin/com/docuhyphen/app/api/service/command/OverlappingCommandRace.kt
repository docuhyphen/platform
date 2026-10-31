package com.docuhyphen.app.api.service.command

import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.service.command.model.OverlappingCommandOutcome
import io.quarkus.arc.Arc
import io.quarkus.narayana.jta.QuarkusTransaction
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

internal class OverlappingCommandRace(private val dataSource: DataSource) {
    fun <T> whileRowLocked(table: String, id: UUID, attempts: List<() -> T>): OverlappingCommandOutcome<T> {
        val pool = Executors.newFixedThreadPool(attempts.size)
        try {
            dataSource.connection.use { holder ->
                holder.autoCommit = false
                queryString(holder, "SELECT CAST(id AS text) FROM $table WHERE id = ? FOR UPDATE", id)
                val pending = attempts.map { attempt ->
                    CompletableFuture.supplyAsync({ runCatching { inNewTransaction(attempt) } }, pool)
                }
                val overlapped = awaitLockWaiters(attempts.size, pending)
                holder.commit()
                return OverlappingCommandOutcome(overlapped, pending.map { it.get(60, TimeUnit.SECONDS) })
            }
        } finally {
            pool.shutdownNow()
        }
    }

    fun <T> whileRowLockedInTurn(table: String, id: UUID, attempts: List<() -> T>): OverlappingCommandOutcome<T> {
        val pool = Executors.newFixedThreadPool(attempts.size)
        try {
            dataSource.connection.use { holder ->
                holder.autoCommit = false
                queryString(holder, "SELECT CAST(id AS text) FROM $table WHERE id = ? FOR UPDATE", id)
                val pending = mutableListOf<CompletableFuture<Result<T>>>()
                var overlapped = true
                attempts.forEach { attempt ->
                    pending += CompletableFuture.supplyAsync({ runCatching { inNewTransaction(attempt) } }, pool)
                    overlapped = awaitLockWaiters(pending.size, pending) && overlapped
                }
                holder.commit()
                return OverlappingCommandOutcome(overlapped, pending.map { it.get(60, TimeUnit.SECONDS) })
            }
        } finally {
            pool.shutdownNow()
        }
    }

    fun awaitLockWaiters(expected: Int, pending: List<CompletableFuture<*>>): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (pending.none { it.isDone } && System.nanoTime() < deadline) {
            val waiting = dataSource.connection.use { connection ->
                queryInt(connection, "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'")
            }
            if (waiting >= expected) return true
            Thread.sleep(25)
        }
        return false
    }

    fun <T> inNewTransaction(attempt: () -> T): T {
        val context = Arc.container().requestContext()
        context.activate()
        try {
            return QuarkusTransaction.requiringNew().call { attempt() }
        } finally {
            context.terminate()
        }
    }
}
