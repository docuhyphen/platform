package com.docuhyphen.app.api.service.auth.idp

enum class OAuthTokenField(val fieldName: String)
{
    ID_TOKEN("id_token"),
    ACCESS_TOKEN("access_token"),
    TOKEN_TYPE("token_type"),
    REFRESH_TOKEN("refresh_token"),
    EXPIRES_IN("expires_in"),
}

enum class OAuthClaimField(val claimName: String)
{
    EMAIL("email"),
    SUB("sub"),
    OID("oid"),
    PREFERRED_USERNAME("preferred_username"),
    GIVEN_NAME("given_name"),
    FAMILY_NAME("family_name"),
    HD("hd"),
}

