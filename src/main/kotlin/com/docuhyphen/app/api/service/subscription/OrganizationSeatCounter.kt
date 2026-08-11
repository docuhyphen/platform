package com.docuhyphen.app.api.service.subscription

import java.util.UUID

/**
 * Supplies the seat count that Business seat capacity is measured against.
 *
 * A seat is consumed by a unique active, provisioned organization membership. External
 * recipients, no-account recipients, deactivated members, revoked members, and pending
 * invitations are never counted.
 */
interface OrganizationSeatCounter
{
    fun countActiveSeats(organizationId: UUID): Long
}

