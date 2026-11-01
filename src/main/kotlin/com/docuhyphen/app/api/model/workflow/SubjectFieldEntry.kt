package com.docuhyphen.app.api.model.workflow

import com.docuhyphen.app.api.service.workflow.WorkflowSpecJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class SubjectFieldEntry(
    val name: String,
    val type: String,
    val description: String? = null,
    val lookupType: String? = null,
)
{
    companion object
    {
        fun parseList(json: String): List<SubjectFieldEntry> =
            runCatching {
                WorkflowSpecJson.instance.decodeFromString(ListSerializer(serializer()), json)
            }.getOrDefault(emptyList())
    }
}
