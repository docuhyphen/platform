package com.docuhyphen.app.api.service.config

import jakarta.enterprise.context.ApplicationScoped
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest

@ApplicationScoped
class AwsSecretsManagerService
{
    fun getSecretString(secretId: String, region: String): String
    {
        SecretsManagerClient.builder()
            .region(Region.of(region))
            .build()
            .use { client ->
                val request = GetSecretValueRequest.builder()
                    .secretId(secretId)
                    .build()

                val response = client.getSecretValue(request)
                return response.secretString() ?: ""
            }
    }
}

