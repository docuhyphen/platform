import {describe, expect, it} from "vitest";
import {getAuditEventTypeLabel} from "../auditEventTypeLabels.ts";

describe("getAuditEventTypeLabel", () =>
{
    it("returns the friendly label for a known catalog key", () =>
    {
        expect(getAuditEventTypeLabel("audit.search.performed")).toBe("Audit trail searched");
        expect(getAuditEventTypeLabel("document.download")).toBe("Document downloaded");
        expect(getAuditEventTypeLabel("organization.trust.suspended")).toBe("Trusted Organization suspended");
        expect(getAuditEventTypeLabel("organization.trust.published_group.listed"))
            .toBe("Trusted Organization published groups viewed");
        expect(getAuditEventTypeLabel("organization.trust.identity_resolution.allowed"))
            .toBe("Trusted Organization member verified");
        expect(getAuditEventTypeLabel("organization.trust.recipient_validation.allowed"))
            .toBe("Trusted Organization recipient validation allowed");
        expect(getAuditEventTypeLabel("organization.trust.acceptance.denied"))
            .toBe("Trusted Organization acceptance denied");
    });

    it("humanizes an unmapped key as a title-cased fallback instead of showing raw dots/underscores", () =>
    {
        expect(getAuditEventTypeLabel("user.login")).toBe("User Login");
        expect(getAuditEventTypeLabel("some_new.event_type")).toBe("Some New Event Type");
    });
});
