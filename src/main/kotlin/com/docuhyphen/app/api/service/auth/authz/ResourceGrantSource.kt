package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.auth.authz.Grant
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject

interface ResourceGrantSource {
    val supportedKinds: Set<ResourceKind>

    fun grantsFor(
        principal: PrincipalRef,
        resource: ResourceRef,
        resourceContext: ResourceAuthorizationContext,
    ): List<Grant>
}

@ApplicationScoped
class ResourceGrantSourceRegistry {
    @Inject
    @Any
    private lateinit var sources: Instance<ResourceGrantSource>

    private var index: Map<ResourceKind, List<ResourceGrantSource>> = emptyMap()

    @PostConstruct
    fun init() {
        index = indexOf(sources.toList())
    }

    fun sourcesFor(kind: ResourceKind): List<ResourceGrantSource> = index[kind].orEmpty()

    companion object {
        fun of(vararg sources: ResourceGrantSource): ResourceGrantSourceRegistry =
            ResourceGrantSourceRegistry().also { it.index = indexOf(sources.toList()) }

        private fun indexOf(sources: List<ResourceGrantSource>): Map<ResourceKind, List<ResourceGrantSource>> =
            sources.flatMap { source -> source.supportedKinds.map { it to source } }
                .groupBy({ it.first }, { it.second })
    }
}
