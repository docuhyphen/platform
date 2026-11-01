package com.docuhyphen.app.api.service.audit.archive

interface AuditArchiveStorage
{
    fun putObject(key: String, bytes: ByteArray)

    fun getObject(key: String): ByteArray

    fun objectExists(key: String): Boolean

    fun listKeysWithPrefix(prefix: String): List<String>
}

class AuditArchiveObjectAlreadyExistsException(key: String) :
    RuntimeException("Archive object already exists and archived objects are immutable: $key")

class AuditArchiveObjectNotFoundException(key: String) :
    RuntimeException("Archive object not found: $key")
