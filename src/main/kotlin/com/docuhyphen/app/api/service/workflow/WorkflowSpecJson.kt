@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.workflow.WorkflowSpec
import com.docuhyphen.app.api.model.workflow.WorkflowStepSpec
import kotlinx.serialization.json.Json

object WorkflowSpecJson
{
    val instance: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun decode(stepsJson: String): WorkflowSpec = instance.decodeFromString(WorkflowSpec.serializer(), stepsJson)
    fun encode(spec: WorkflowSpec): String = instance.encodeToString(WorkflowSpec.serializer(), spec)

    fun encodeStep(step: WorkflowStepSpec): String =
        instance.encodeToString(WorkflowStepSpec.serializer(), step)

    fun decodeStep(json: String): WorkflowStepSpec =
        instance.decodeFromString(WorkflowStepSpec.serializer(), json)
}



