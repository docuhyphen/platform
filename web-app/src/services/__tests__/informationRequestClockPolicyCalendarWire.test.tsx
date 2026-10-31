/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterAll, afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import apiClient, {setApiClientAuthToken} from "../apiClient.ts";
import {jsonAnswer, LocalApiServer, startLocalApiServer} from "../testing/localApiServer.ts";
import {
    InformationRequestClockDueEffect,
    InformationRequestClockPolicyDto,
    InformationRequestClockType,
    InformationRequestOwnerType,
    InformationRequestWorkingPeriodDto,
} from "../../app/models/models.tsx";
import ClockPoliciesPanel from "../../app/information-requests/operations/clock-policies/ClockPoliciesPanel.tsx";

const policyWith = (id: string, workingPeriods: InformationRequestWorkingPeriodDto[]): InformationRequestClockPolicyDto => ({
    id,
    policyKey: id,
    displayName: id,
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    versions: [{
        id: `${id}-version-1`,
        versionNumber: 1,
        clockType: InformationRequestClockType.BUSINESS,
        businessTimezone: "Europe/Berlin",
        standardDurationMinutes: 480,
        urgentDurationMinutes: 120,
        escalationAfterMinutes: null,
        dueEffect: InformationRequestClockDueEffect.MARK_OVERDUE,
        workingPeriods,
        holidays: [],
        reminderMinutesBeforeDue: [],
        reminderCommunicationId: null,
        overdueCommunicationId: null,
        publishedAt: Date.parse("2026-03-01T08:00:00Z"),
    }],
});

const policies = [
    policyWith("split-response", [
        {dayOfWeek: "MONDAY", startMinute: 540, endMinute: 720},
        {dayOfWeek: "MONDAY", startMinute: 780, endMinute: 1020},
    ]),
    policyWith("varied-response", [
        {dayOfWeek: "MONDAY", startMinute: 540, endMinute: 1020},
        {dayOfWeek: "FRIDAY", startMinute: 540, endMinute: 780},
    ]),
    policyWith("full-day-response", [{dayOfWeek: "MONDAY", startMinute: 0, endMinute: 1440}]),
    policyWith("evening-response", [{dayOfWeek: "SATURDAY", startMinute: 1020, endMinute: 1440}]),
];

let server: LocalApiServer;

const publishWithNewUrgentHours = async (policy: InformationRequestClockPolicyDto) => {
    render(<ClockPoliciesPanel canManage={true}
                               ownerType={InformationRequestOwnerType.ORGANIZATION}/>);
    fireEvent.click(await screen.findByRole("button", {name: `Publish a new version of ${policy.displayName}`}));
    const dialog = within(await screen.findByRole("dialog", {name: `New version of ${policy.displayName}`}));
    fireEvent.change(dialog.getByLabelText(/Urgent hours/), {target: {value: "1"}});
    const publish = dialog.getByRole("button", {name: "Publish version"}) as HTMLButtonElement;
    expect(publish.disabled).toBe(false);
    fireEvent.click(publish);
    const path = `/information-request-clock-policies/${policy.id}/versions`;
    return waitFor(() => {
        const request = server.received.find(received => received.method === "POST" && received.path === path);
        expect(request).toBeTruthy();
        return JSON.parse(request?.body ?? "{}") as Record<string, unknown>;
    });
};

describe("Information Request due date policy calendars published again over HTTP", () => {
    beforeAll(async () => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
        server = await startLocalApiServer((method, path) => {
            if (method === "GET" && path.split("?")[0] === "/information-request-clock-policies") return jsonAnswer(200, policies);
            const found = policies.find(policy => path === `/information-request-clock-policies/${policy.id}/versions`);
            return method === "POST" && found
                ? jsonAnswer(201, found)
                : jsonAnswer(404, {reasonCode: "INFORMATION_REQUEST_NOT_FOUND", errorMessage: "Not found"});
        });
        apiClient.defaults.adapter = "xhr";
        apiClient.defaults.baseURL = server.origin;
        setApiClientAuthToken(null);
    });

    afterEach(() => cleanup());

    afterAll(async () => {
        vi.unstubAllGlobals();
        await server.close();
    });

    for (const policy of policies) {
        it(`publishes ${policy.id} with every working period unchanged when only its urgent hours change`, async () => {
            const sent = await publishWithNewUrgentHours(policy);

            expect(sent.urgentDurationMinutes).toBe(60);
            expect(sent.workingPeriods).toEqual(policy.versions[0].workingPeriods);
        });
    }
});
