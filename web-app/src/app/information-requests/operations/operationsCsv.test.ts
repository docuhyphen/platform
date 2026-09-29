import {describe, expect, it} from "vitest";
import {
    InformationRequestNoticeDeliveryState,
    InformationRequestOperationsException,
    InformationRequestOperationsRowDto,
    InformationRequestShareRoleKey,
    InformationRequestSlaStatus,
    InformationRequestState,
} from "../../models/models.tsx";
import {operationsCsv} from "./operationsCsv.ts";

const row = (overrides: Partial<InformationRequestOperationsRowDto>): InformationRequestOperationsRowDto => ({
    requestId: "request-a",
    exchangeId: "exchange-a",
    title: "Periodic records request",
    assignees: [
        {roleKey: InformationRequestShareRoleKey.CONTRIBUTOR, principalKind: "USER", principalId: "user-b", label: "Morgan Lee"},
        {roleKey: InformationRequestShareRoleKey.SUBJECT, principalKind: "PARTICIPANT", principalId: "participant-c"},
    ],
    templateVersionId: "version-a",
    state: InformationRequestState.IN_PROGRESS,
    gatesExchangeClosure: true,
    createdAt: "2026-09-20T08:00:00Z",
    ageSeconds: 3 * 86400 + 2 * 3600 + 59,
    slaStatus: InformationRequestSlaStatus.OVERDUE,
    nearestDueAt: "2026-09-25T08:00:00Z",
    clockCount: 1,
    reminderCount: 2,
    noticeCounts: {[InformationRequestNoticeDeliveryState.DELIVERED]: 2},
    exceptionCounts: {[InformationRequestOperationsException.NOTICE_UNDELIVERABLE]: 1},
    ...overrides,
});

describe("operationsCsv", () =>
{
    it("writes one header and one line per row in words and machine-readable times", () =>
    {
        const lines = operationsCsv([row({})]).split("\r\n");

        expect(lines[0]).toBe("Request id,Title,State,Service level,Due,Created,Age in hours,Reminders,Assignees,Notices,Needs attention");
        expect(lines[1]).toBe(
            "request-a,Periodic records request,In progress,Overdue,2026-09-25T08:00:00Z,2026-09-20T08:00:00Z,74,2," +
            "\"Morgan Lee (Contributor); Unnamed party (Subject)\",2 delivered,1 undeliverable notices",
        );
        expect(lines).toHaveLength(2);
    });

    it("quotes separators and quotes, and keeps a spreadsheet from reading a value as a formula", () =>
    {
        const lines = operationsCsv([
            row({title: "Records, \"annual\"", nearestDueAt: undefined, assignees: [], noticeCounts: {}, exceptionCounts: {}}),
            row({requestId: "request-b", title: "=HYPERLINK(\"x\")"}),
            row({requestId: "request-c", title: "+1 day"}),
        ]).split("\r\n");

        expect(lines[1]).toBe("request-a,\"Records, \"\"annual\"\"\",In progress,Overdue,,2026-09-20T08:00:00Z,74,2,,,");
        expect(lines[2].split(",")[1]).toBe("\"'=HYPERLINK(\"\"x\"\")\"");
        expect(lines[3].split(",")[1]).toBe("'+1 day");
    });
});
