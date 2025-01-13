package com.securedocsshare.app.repository

import com.securedocsshare.app.api.model.AppUser
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery

@RequestScoped
class AuthenticationRepository : BaseRepository<AppUser>(AppUser::class.java)
{

}
