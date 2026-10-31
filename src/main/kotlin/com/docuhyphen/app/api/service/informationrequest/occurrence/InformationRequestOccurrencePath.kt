package com.docuhyphen.app.api.service.informationrequest.occurrence

object InformationRequestOccurrencePath
{
    const val ROOT = "root"

    fun isRoot(occurrencePath: String): Boolean = occurrencePath == ROOT

    fun isActiveOccurrence(occurrencePath: String, activeOccurrencePaths: Set<String>): Boolean =
        isRoot(occurrencePath) || occurrencePath in activeOccurrencePaths

    fun isWithin(path: String, ancestor: String): Boolean =
        path == ancestor || path.startsWith("$ancestor/")

    fun groupOccurrencePath(groupKey: String, index: Int, parentPath: String?): String =
        (parentPath?.let { "$it/" } ?: "") + "$groupKey[$index]"

    fun nextIdentityIndex(groupKey: String, parentPath: String?, siblingPaths: List<String>): Int = siblingPaths
        .mapNotNull { identityIndexOf(groupKey, parentPath, it) }
        .maxOrNull()
        ?.plus(1)
        ?: 0

    private fun identityIndexOf(groupKey: String, parentPath: String?, occurrencePath: String): Int? {
        val prefix = (parentPath?.let { "$it/" } ?: "") + "$groupKey["
        if (!occurrencePath.startsWith(prefix) || !occurrencePath.endsWith("]"))
            return null
        return occurrencePath
            .substring(prefix.length, occurrencePath.length - 1)
            .toIntOrNull()
    }
}

