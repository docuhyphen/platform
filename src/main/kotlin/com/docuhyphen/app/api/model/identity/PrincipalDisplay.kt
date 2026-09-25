package com.docuhyphen.app.api.model.identity

data class PrincipalDisplay(
    val name: String?,
    val email: String?,
)
{
    companion object
    {
        val UNKNOWN = PrincipalDisplay(name = null, email = null)
    }
}
