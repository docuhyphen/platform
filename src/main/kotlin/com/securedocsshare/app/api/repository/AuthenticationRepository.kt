package com.securedocsshare.app.api.repository

import com.securedocsshare.app.api.model.AppUser
import jakarta.enterprise.context.RequestScoped

@RequestScoped
class AuthenticationRepository : BaseRepository<AppUser>(AppUser::class.java)
{

}
