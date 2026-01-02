package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.OrganizationGroup
import jakarta.enterprise.context.RequestScoped

@RequestScoped
class OrganizationGroupRepository : BaseRepository<OrganizationGroup>(OrganizationGroup::class.java)
{}