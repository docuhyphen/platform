package com.docuhyphen.app.api.model.user

data class AvatarContent(val bytes: ByteArray, val contentType: String)
{
    override fun equals(other: Any?): Boolean
    {
        if (this === other) return true
        if (other !is AvatarContent) return false
        return contentType == other.contentType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + contentType.hashCode()
}
