import {describe, expect, it} from "vitest";
import {formatInformationRequestTime} from "../shared/informationRequestFormatting.ts";
import {auditActorText, formattedTime} from "./operationsLabels.ts";

describe("formattedTime", () =>
{
    it("states a time the way every Information Request surface does, with the viewer's time zone", () =>
    {
        expect(formattedTime(Date.parse("2026-09-25T08:00:00Z"))).toBe(formatInformationRequestTime(Date.parse("2026-09-25T08:00:00Z")));
        expect(formattedTime(undefined)).toBe("Not set");
    });

    it("says a time the server sends as null is not set, while epoch zero is still a time", () => {
        expect(formattedTime(null)).toBe("Not set");
        expect(formattedTime(0)).toBe(formatInformationRequestTime(0));
    });
});

describe("auditActorText", () => {
    it("names who acted in plain words and never shows the raw actor kind", () => {
        expect(auditActorText("HUMAN")).toBe("a person");
        expect(auditActorText("PUBLIC_LINK")).toBe("an access-link respondent");
        expect(auditActorText("PARTICIPANT")).toBe("a participant");
        expect(auditActorText("APP")).toBe("an application");
        expect(auditActorText("WORKFLOW")).toBe("a Workflow");
        expect(auditActorText("SYSTEM")).toBe("the system");
        expect(auditActorText(undefined)).toBe("the platform");
        expect(auditActorText("NEW_KIND")).toBe("new kind");
    });
});
