package com.docuhyphen.app.api.model.entity

enum class SubjectIdentityOwnerType
{
    ORGANIZATION,
    USER,
}

enum class SubjectKind
{
    PERSON,
    ORGANIZATION,
    ASSET,
    RECORD,
    OTHER,
}

enum class SubjectIdentityTransitionKind
{
    MERGED,
    SUPERSEDED,
}
