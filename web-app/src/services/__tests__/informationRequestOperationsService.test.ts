// @vitest-environment jsdom
import axios, {AxiosRequestConfig} from "axios";
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../apiClient.ts";
import {
    getInformationRequestAuditEvents,
    getInformationRequestOperations
} from "../informationRequestOperationsService.ts";
import {
    InformationRequestOperationsException,
    InformationRequestSlaStatus,
    InformationRequestState,
} from "../../app/models/models.tsx";

vi.mock("../apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn()}}));

const lastRequestUri = (): string => {
    const [url, config] = (vi.mocked(apiClient.get).mock.lastCall ?? []) as [string, AxiosRequestConfig];
    return axios.getUri({...config, url});
};

describe("Information Request operations transport", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(apiClient.get).mockResolvedValue({data: {items: [], total: 0, limit: 25, offset: 0}});
    });

    it("asks the queue for its states, Exchange, search, assignee, service level, exception kinds, and page", async () =>
    {
        await getInformationRequestOperations({
            states: [InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS],
            exchangeId: "exchange-a",
            search: "records",
            assigneeId: "user-b",
            slaStatus: InformationRequestSlaStatus.OVERDUE,
            exceptions: [InformationRequestOperationsException.NOTICE_FAILED],
            exceptionsOnly: true,
            limit: 25,
            offset: 50,
        });

        expect(apiClient.get).toHaveBeenLastCalledWith("/information-request-operations", expect.objectContaining({
            params: {
                state: [InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS],
                exchangeId: "exchange-a",
                search: "records",
                assigneeId: "user-b",
                slaStatus: InformationRequestSlaStatus.OVERDUE,
                exception: [InformationRequestOperationsException.NOTICE_FAILED],
                exceptionsOnly: true,
                limit: 25,
                offset: 50,
            },
        }));
    });

    it("sends each chosen state and exception kind as its own repeated query parameter", async () => {
        await getInformationRequestOperations({
            states: [InformationRequestState.DRAFT, InformationRequestState.ISSUED],
            exceptions: [
                InformationRequestOperationsException.NOTICE_FAILED,
                InformationRequestOperationsException.CLOCK_ESCALATED,
            ],
            exceptionsOnly: false,
            limit: 25,
            offset: 0,
        });

        const uri = lastRequestUri();
        expect(uri).toContain("state=DRAFT&state=ISSUED");
        expect(uri).toContain("exception=NOTICE_FAILED&exception=CLOCK_ESCALATED");
        expect(uri).not.toMatch(/%5B|\[/);
    });

    it("leaves out filters that are not set", async () =>
    {
        await getInformationRequestOperations({
            states: [],
            search: "  ",
            exceptions: [],
            exceptionsOnly: false,
            limit: 25,
            offset: 0
        });

        expect(apiClient.get).toHaveBeenLastCalledWith("/information-request-operations", expect.objectContaining({
            params: {
                state: undefined,
                exchangeId: undefined,
                search: undefined,
                assigneeId: undefined,
                slaStatus: undefined,
                exception: undefined,
                exceptionsOnly: undefined,
                limit: 25,
                offset: 0,
            },
        }));
        expect(lastRequestUri()).toBe("/information-request-operations?limit=25&offset=0");
    });

    it("asks for one page of a request's audit history", async () => {
        await getInformationRequestAuditEvents("request-a", {limit: 50, offset: 100});

        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/audit-events", {
            params: {limit: 50, offset: 100},
        });
    });
});
