package com.docuhyphen.app.api.exception

/**
 * Thrown when a direct Exchange status write is blocked because a running workflow instance
 * must gate the transition first. Maps to HTTP 409 Conflict.
 *
 * The calling method is annotated with `@Transactional(dontRollbackOn = [WorkflowConflictException::class])`
 * so the transaction carrying the newly started workflow instance still commits.
 */
class WorkflowConflictException(message: String) : RuntimeException(message)


