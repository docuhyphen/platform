package com.docuhyphen.app.api.service.fields

import kotlinx.serialization.Serializable

/**
 * Type-specific validation constraints stored as `constraints_json` on a Field Contract.
 * All properties are optional; a null constraint is not enforced. Numeric and date bounds are
 * strings so exact decimal and ISO date semantics survive serialization.
 */
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

/**
 * A selectable option for SINGLE_SELECT / MULTI_SELECT contracts. [code] is the immutable stored
 * business value; [label] is mutable presentation. Deactivating an option prevents new selection
 * but never erases historical values.
 */
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
