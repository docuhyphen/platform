package com.docuhyphen.app.api.exception

class DocumentVersionObjectKeyInUseException(key: String) :
    RuntimeException("Document version storage key already holds content: $key")

class DocumentVersionContentNotFoundException(locator: String) :
    RuntimeException("Document version content not found: $locator")

class DocumentVersionContentDigestMismatchException(key: String) :
    RuntimeException("Document version content written under $key does not match its expected digest")

class DocumentVersionContentIntegrityException(locator: String) :
    RuntimeException("Document version content no longer matches its recorded digest: $locator")
