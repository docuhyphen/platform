package com.docuhyphen.app.api.service.subscription

import java.util.*

interface OrganizationSeatCounter
{
    fun countActiveSeats(organizationId: UUID): Long
}

