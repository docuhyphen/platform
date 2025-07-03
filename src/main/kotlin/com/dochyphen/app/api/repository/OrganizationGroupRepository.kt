package com.dochyphen.app.api.repository

import com.dochyphen.app.api.model.entity.OrganizationGroup
import jakarta.enterprise.context.RequestScoped

@RequestScoped
class OrganizationGroupRepository : BaseRepository<OrganizationGroup>(OrganizationGroup::class.java)
{}