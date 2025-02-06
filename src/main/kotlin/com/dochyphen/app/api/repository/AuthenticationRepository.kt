package com.dochyphen.app.api.repository

import com.dochyphen.app.api.model.entity.AppUser
import jakarta.enterprise.context.RequestScoped

@RequestScoped
class AuthenticationRepository : BaseRepository<AppUser>(AppUser::class.java)
{

}
