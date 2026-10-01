package com.docuhyphen.app.api.repository.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.RequestScoped

@RequestScoped
class AuthenticationRepository : BaseRepository<AppUser>(AppUser::class.java)
