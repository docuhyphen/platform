package com.docuhyphen.app.api.model.fields

import com.docuhyphen.app.api.service.fields.FieldsJson
import kotlinx.serialization.Serializable

@Serializable
data class FieldConstraints(
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val minValue: String? = null,
    val maxValue: String? = null,
    val scale: Int? = null,
    val minSelections: Int? = null,
    val maxSelections: Int? = null,
    val minDate: String? = null,
    val maxDate: String? = null,
)
{
    companion object
    {
        val EMPTY = FieldConstraints()

        fun parse(json: String?): FieldConstraints =
            if (json.isNullOrBlank()) EMPTY
            else runCatching {
                FieldsJson.instance.decodeFromString(serializer(), json)
            }.getOrDefault(EMPTY)
    }
}

@Serializable
data class FieldOption(
    val code: String,
    val label: String,
    val order: Int = 0,
    val active: Boolean = true,
    val externalMappings: Map<String, String> = emptyMap(),
)
{
    companion object
    {
        fun parseList(json: String?): List<FieldOption> =
            if (json.isNullOrBlank()) emptyList()
            else runCatching {
                FieldsJson.instance.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(serializer()), json,
                )
            }.getOrDefault(emptyList())

        fun encodeList(options: List<FieldOption>): String =
            FieldsJson.instance.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(serializer()), options,
            )
    }
}
