/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterAll, afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import apiClient, {setApiClientAuthToken} from "../apiClient.ts";
import {jsonAnswer, LocalAnswer, LocalApiServer, startLocalApiServer} from "../testing/localApiServer.ts";
import {
    InformationRequestClockDueEffect,
    InformationRequestClockPolicyDto,
    InformationRequestClockPolicyVersionDto,
    InformationRequestClockType,
    InformationRequestOwnerType,
} from "../../app/models/models.tsx";
import ClockPoliciesPanel from "../../app/information-requests/operations/clock-policies/ClockPoliciesPanel.tsx";

const version = (id: string, escalation: Partial<InformationRequestClockPolicyVersionDto>): InformationRequestClockPolicyVersionDto => ({
    id,
    versionNumber: 1,
    clockType: InformationRequestClockType.CALENDAR,
    businessTimezone: "UTC",
    standardDurationMinutes: 1440,
    urgentDurationMinutes: 480,
    ...escalation,
    dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
    workingPeriods: [],
    holidays: [],
    reminderMinutesBeforeDue: [],
    reminderCommunicationId: null,
    overdueCommunicationId: null,
    publishedAt: Date.parse("2026-09-01T08:00:00Z"),
});

const policyOf = (id: string, displayName: string, escalation: Partial<InformationRequestClockPolicyVersionDto>): InformationRequestClockPolicyDto => ({
    id,
    policyKey: id,
    displayName,
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    versions: [version(`${id}-version-1`, escalation)],
});

const policies: InformationRequestClockPolicyDto[] = [
    policyOf("policy-null", "Unescalated response", {escalationAfterMinutes: null}),
    policyOf("policy-omitted", "Plain response", {}),
    policyOf("policy-zero", "Escalated at due", {escalationAfterMinutes: 0}),
    policyOf("policy-positive", "Escalated later", {escalationAfterMinutes: 120}),
];

const notFound = jsonAnswer(404, {reasonCode: "INFORMATION_REQUEST_NOT_FOUND", errorMessage: "Not found"});

const answers: Record<string, LocalAnswer> = {
    "GET /information-request-clock-policies": jsonAnswer(200, policies),
    ...Object.fromEntries(policies.map(policy => [
        `POST /information-request-clock-policies/${policy.id}/versions`,
        jsonAnswer(201, policy),
    ])),
};

let server: LocalApiServer;

const renderPanel = () =>
    render(<ClockPoliciesPanel canManage={true}
                               ownerType={InformationRequestOwnerType.ORGANIZATION}/>);

const summaryOf = (policyId: string) =>
    document.getElementById(`information-request-clock-policy-${policyId}-summary`)?.textContent;

const includingModalHidden = {hidden: true} as const;

const publishWithNewUrgentHours = async (policy: InformationRequestClockPolicyDto) => {
    fireEvent.click(await screen.findByRole("button", {
        name: `Publish a new version of ${policy.displayName}`,
        ...includingModalHidden,
    }));
    const dialog = await screen.findByRole("dialog", {name: `New version of ${policy.displayName}`, ...includingModalHidden});
    const escalation = (within(dialog).getByLabelText(/Escalate after/) as HTMLInputElement).value;
    fireEvent.change(within(dialog).getByLabelText(/Urgent hours/), {target: {value: "4"}});
    fireEvent.click(within(dialog).getByRole("button", {name: "Publish version", ...includingModalHidden}));
    const path = `/information-request-clock-policies/${policy.id}/versions`;
    const sent = await waitFor(() => {
        const request = server.received.find(received => received.method === "POST" && received.path === path);
        expect(request).toBeTruthy();
        return JSON.parse(request?.body ?? "{}") as Record<string, unknown>;
    });
    await waitFor(() => expect(screen.queryByRole("dialog", {
        name: `New version of ${policy.displayName}`,
        ...includingModalHidden,
    })).toBeNull());
    return {escalation, sent};
};

describe("Information Request due date policies read and published over HTTP as the server sends them", () => {
    beforeAll(async () => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
        server = await startLocalApiServer((method, path) => answers[`${method} ${path.split("?")[0]}`] ?? notFound);
        apiClient.defaults.adapter = "xhr";
        apiClient.defaults.baseURL = server.origin;
        setApiClientAuthToken(null);
    });

    afterEach(() => cleanup());

    afterAll(async () => {
        vi.unstubAllGlobals();
        await server.close();
    });

    it("sends a version's unset escalation and notices as explicit nulls", () => {
        const body = answers["GET /information-request-clock-policies"].body;

        expect(body).toContain(`"escalationAfterMinutes":null`);
        expect(body).toContain(`"reminderCommunicationId":null,"overdueCommunicationId":null`);
    });

    it("describes escalation only for versions that escalate, including at the due time", async () => {
        renderPanel();

        await screen.findByText(/Escalated 2 hours after due\./);
        expect(summaryOf("policy-null")).toBe("Calendar time in UTC. Standard 24 hours, urgent 8 hours. When due, the request is marked overdue.");
        expect(summaryOf("policy-omitted")).not.toContain("Escalated");
        expect(summaryOf("policy-zero")).toContain("Escalated 0 hours after due.");
        expect(summaryOf("policy-positive")).toContain("Escalated 2 hours after due.");
    });

    it("keeps each version's escalation when only its urgent hours change and it is published again", {timeout: 20000}, async () => {
        renderPanel();

        const published: { escalation: string; sent: Record<string, unknown> }[] = [];
        for (const policy of policies) published.push(await publishWithNewUrgentHours(policy));

        expect(published.map(({escalation}) => escalation)).toEqual(["", "", "0", "2"]);
        expect(published.map(({sent}) => sent.urgentDurationMinutes)).toEqual([240, 240, 240, 240]);
        expect(published.map(({sent}) => sent.escalationAfterMinutes)).toEqual([undefined, undefined, 0, 120]);
        expect(published[0].sent).not.toHaveProperty("escalationAfterMinutes");
        expect(published[1].sent).not.toHaveProperty("escalationAfterMinutes");
        published.forEach(({sent}) => {
            expect(sent).not.toHaveProperty("reminderCommunicationId");
            expect(sent).not.toHaveProperty("overdueCommunicationId");
        });
    });
});
