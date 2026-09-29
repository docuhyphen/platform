// @vitest-environment jsdom
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../apiClient.ts";
import {getInformationRequestOperations} from "../informationRequestOperationsService.ts";
import {InformationRequestSlaStatus} from "../../app/models/models.tsx";

vi.mock("../apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn()}}));

describe("Information Request operations transport", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(apiClient.get).mockResolvedValue({data: {items: [], total: 0, limit: 25, offset: 0}});
    });

    it("asks the queue for its search, assignee, service level, exceptions, and page", async () =>
    {
        await getInformationRequestOperations({
            search: "records",
            assigneeId: "user-b",
            slaStatus: InformationRequestSlaStatus.OVERDUE,
            exceptionsOnly: true,
            limit: 25,
            offset: 50,
        });

        expect(apiClient.get).toHaveBeenLastCalledWith("/information-request-operations", {
            params: {
                search: "records",
                assigneeId: "user-b",
                slaStatus: InformationRequestSlaStatus.OVERDUE,
                exceptionsOnly: true,
                limit: 25,
                offset: 50,
            },
        });
    });

    it("leaves out filters that are not set", async () =>
    {
        await getInformationRequestOperations({search: "  ", exceptionsOnly: false, limit: 25, offset: 0});

        expect(apiClient.get).toHaveBeenLastCalledWith("/information-request-operations", {
            params: {
                search: undefined,
                assigneeId: undefined,
                slaStatus: undefined,
                exceptionsOnly: undefined,
                limit: 25,
                offset: 0,
            },
        });
    });
});
