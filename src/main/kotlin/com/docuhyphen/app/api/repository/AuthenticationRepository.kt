package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AppUser
import jakarta.enterprise.context.RequestScoped

@RequestScoped
class AuthenticationRepository : BaseRepository<AppUser>(AppUser::class.java)
