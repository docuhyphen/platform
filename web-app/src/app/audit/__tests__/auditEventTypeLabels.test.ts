import {describe, expect, it} from "vitest";
import {getAuditEventTypeLabel} from "../auditEventTypeLabels.ts";

describe("getAuditEventTypeLabel", () =>
{
    it("returns the friendly label for a known catalog key", () =>
    {
        expect(getAuditEventTypeLabel("audit.search.performed")).toBe("Audit trail searched");
        expect(getAuditEventTypeLabel("document.download")).toBe("Document downloaded");
    });

    it("humanizes an unmapped key as a title-cased fallback instead of showing raw dots/underscores", () =>
    {
        expect(getAuditEventTypeLabel("user.login")).toBe("User Login");
        expect(getAuditEventTypeLabel("some_new.event_type")).toBe("Some New Event Type");
    });
});
