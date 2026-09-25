package com.docuhyphen.app.api.service.informationrequest

/**
 * The occurrence path an Information Request Requirement, response, or Field Value Set is anchored
 * to. Requirements outside any repeatable group sit on [ROOT].
 */
object InformationRequestOccurrencePath
{
    const val ROOT = "root"

    fun isRoot(occurrencePath: String): Boolean = occurrencePath == ROOT

    fun isActiveOccurrence(occurrencePath: String, activeOccurrencePaths: Set<String>): Boolean =
        isRoot(occurrencePath) || occurrencePath in activeOccurrencePaths
}

