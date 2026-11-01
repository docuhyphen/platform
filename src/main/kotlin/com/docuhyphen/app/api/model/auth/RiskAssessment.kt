package com.docuhyphen.app.api.model.auth

enum class RiskLevel
{ NONE, LOW, MEDIUM, HIGH }

data class RiskAssessment(
    val level: RiskLevel,
    val reasons: List<String>,
)
