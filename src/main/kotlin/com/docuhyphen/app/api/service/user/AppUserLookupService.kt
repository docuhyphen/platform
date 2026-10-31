package com.docuhyphen.app.api.service.user

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.repository.user.AppUserRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class AppUserLookupService @Inject constructor(
    private val appUserRepository: AppUserRepository,
) {
    fun findById(appUserId: UUID): AppUser? = appUserRepository.findById(appUserId)
}
