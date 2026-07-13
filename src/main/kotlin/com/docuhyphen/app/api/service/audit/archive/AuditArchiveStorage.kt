package com.docuhyphen.app.api.service.audit.archive

/**
 * Write/read abstraction over wherever WORM-archived segment/manifest objects live. Two
 * implementations: [LocalAuditArchiveStorage] (default; a local directory, used in dev/CI and as
 * the degraded fallback) and [S3AuditArchiveStorage] (an existing-service S3 bucket with
 * versioning and Object Lock Governance mode).
 *
 * Deliberately has no delete/overwrite method: the application only ever needs to
 * `PutObject`/`GetObject` archived evidence, matching the IAM constraint that the app task role
 * must not be able to delete or bypass retention on archived objects.
 */
interface AuditArchiveStorage
{
    /** Writes [bytes] under [key]. Implementations must refuse to silently overwrite an existing key. */
    fun putObject(key: String, bytes: ByteArray)

    fun getObject(key: String): ByteArray

    fun objectExists(key: String): Boolean

    /**
     * Every object key currently stored under [prefix]. Used to reconcile what has actually been
     * archived against the mutable `audit_archive_segment` rows, so a deleted segment row alone
     * cannot make a covered-but-hidden range look unarchived (or make a genuinely tampered range
     * look clean because its row was removed).
     */
    fun listKeysWithPrefix(prefix: String): List<String>
}

class AuditArchiveObjectAlreadyExistsException(key: String) :
    RuntimeException("Archive object already exists and archived objects are immutable: $key")

class AuditArchiveObjectNotFoundException(key: String) :
    RuntimeException("Archive object not found: $key")
