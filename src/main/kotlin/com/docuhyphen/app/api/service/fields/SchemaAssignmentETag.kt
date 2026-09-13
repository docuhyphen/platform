package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.SchemaAssignment

/**
 * The entity tag that names one exact state of a [SchemaAssignment].
 *
 * An assignment is written once and never rewritten: choosing a Schema for a resource creates it and
 * removing that Schema deletes it, so the assignment's identity is what separates one state from the
 * next. A caller that names the assignment it read therefore cannot spend that claim against the
 * assignment which replaced it, which is exactly what a removal has to be protected from.
 *
 * The first operation that rewrites an assignment rather than replacing it gives the row a count of
 * its changes and derives the tag from both, without changing what a caller sends back.
 */
object SchemaAssignmentETag
{
    /** The validator for the state [assignment] currently stands in, quoted as a strong tag. */
    fun of(assignment: SchemaAssignment): String = "\"${assignment.id}\""
}
