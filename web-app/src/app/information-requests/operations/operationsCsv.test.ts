import {describe, expect, it} from "vitest";
import {
    InformationRequestExecutionStandingKind,
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
    createdAt: Date.parse("2026-09-20T08:00:00Z"),
    issuedAt: Date.parse("2026-09-20T09:00:00Z"),
    firstViewedAt: Date.parse("2026-09-21T09:00:00Z"),
    startedAt: null,
    ageSeconds: 3 * 86400 + 2 * 3600 + 59,
    slaStatus: InformationRequestSlaStatus.OVERDUE,
    nearestDueAt: Date.parse("2026-09-25T08:00:00Z"),
    clockCount: 1,
    reminderCount: 2,
    noticeCounts: {[InformationRequestNoticeDeliveryState.DELIVERED]: 2},
    exceptionCounts: {[InformationRequestOperationsException.NOTICE_UNDELIVERABLE]: 1},
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
    ...overrides,
});

describe("operationsCsv", () =>
{
    it("writes one header and one line per row in words and machine-readable times", () =>
    {
        const lines = operationsCsv([row({})]).split("\r\n");

        expect(lines[0]).toBe(
            "Request id,Title,State,Service level,Due,Created,Issued,First viewed,Started,Age in hours,Clock reminder points," +
            "Assignees,Notices,Needs attention",
        );
        expect(lines[1]).toBe(
            "request-a,Periodic records request,In progress,Overdue,2026-09-25T08:00:00.000Z,2026-09-20T08:00:00.000Z," +
            "2026-09-20T09:00:00.000Z,2026-09-21T09:00:00.000Z,,74,2," +
            "\"Morgan Lee (Contributor); Unnamed party (Subject)\",2 delivered,1 undeliverable notice",
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

        expect(lines[1]).toBe(
            "request-a,\"Records, \"\"annual\"\"\",In progress,Overdue,,2026-09-20T08:00:00.000Z," +
            "2026-09-20T09:00:00.000Z,2026-09-21T09:00:00.000Z,,74,2,,,",
        );
        expect(lines[2].split(",")[1]).toBe("\"'=HYPERLINK(\"\"x\"\")\"");
        expect(lines[3].split(",")[1]).toBe("'+1 day");
    });

    it.each([
        ["a minus sign", "-2 days late", "'-2 days late"],
        ["an at sign", "@review", "'@review"],
        ["a tab", "\tindented", "'\tindented"],
        ["a carriage return", "\rreturned", "\"'\rreturned\""],
    ])("keeps a value starting with %s from being read as a formula", (_, title, written) => {
        const [, line] = operationsCsv([row({
            title,
            assignees: [],
            noticeCounts: {},
            exceptionCounts: {}
        })]).split("\r\n").slice(0, 2);

        expect(line.startsWith(`request-a,${written},`)).toBe(true);
    });

    it("leaves Due empty for a request the server sends without a deadline, and writes epoch zero as a time", () => {
        const lines = operationsCsv([
            row({slaStatus: InformationRequestSlaStatus.NO_CLOCK, nearestDueAt: null, clockCount: 0}),
            row({requestId: "request-b", nearestDueAt: 0}),
        ]).split("\r\n");

        expect(lines[1].split(",").slice(3, 6)).toEqual(["No clock", "", "2026-09-20T08:00:00.000Z"]);
        expect(lines[2].split(",")[4]).toBe("1970-01-01T00:00:00.000Z");
    });

    it("leaves Issued, First viewed, and Started empty until each happens", () => {
        const lines = operationsCsv([
            row({state: InformationRequestState.DRAFT, issuedAt: null, firstViewedAt: null, startedAt: null}),
            row({requestId: "request-b", startedAt: Date.parse("2026-09-22T09:00:00Z")}),
        ]).split("\r\n");

        expect(lines[1].split(",").slice(6, 9)).toEqual(["", "", ""]);
        expect(lines[2].split(",").slice(6, 9))
            .toEqual(["2026-09-20T09:00:00.000Z", "2026-09-21T09:00:00.000Z", "2026-09-22T09:00:00.000Z"]);
    });
});
