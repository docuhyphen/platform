package com.docuhyphen.app.api.model.auth

data class DpopVerifyResult(val valid: Boolean, val jwkThumbprint: String? = null, val reason: String? = null)
