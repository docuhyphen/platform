package com.docuhyphen.app.api.resource.communication.operations

import com.docuhyphen.app.api.resource.model.SalesEnquiryRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("no-auth/sales-enquiries")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface SalesEnquiryResourceOperations {
    @POST
    fun submitSalesEnquiry(request: SalesEnquiryRequest): Response
}
