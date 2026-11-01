package com.docuhyphen.app.api.service.scim

class ScimRequestException(
    val statusCode: Int,
    val detail: String,
    val scimType: String? = null,
) : RuntimeException(detail)
{
    companion object
    {
        fun badRequest(detail: String) = ScimRequestException(400, detail)
        fun unauthorized(detail: String) = ScimRequestException(401, detail)
        fun notFound(detail: String) = ScimRequestException(404, detail)
        fun conflict(detail: String, scimType: String) = ScimRequestException(409, detail, scimType)
        fun notImplemented(detail: String) = ScimRequestException(501, detail)
        fun serviceUnavailable(detail: String) = ScimRequestException(503, detail)
    }
}
