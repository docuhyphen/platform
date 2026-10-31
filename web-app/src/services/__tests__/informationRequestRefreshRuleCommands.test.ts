import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../apiClient.ts";
import {
    createInformationRequestRefresh,
    defineInformationRequestRefreshRule,
} from "../informationRequestAuthoringService.ts";

vi.mock("../apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn(), put: vi.fn()}}));

describe("refresh rule commands", () => {
    beforeEach(() => vi.clearAllMocks());

    it("defines a refresh rule under the request ETag and creates a refresh under its own key", async () => {
        vi.mocked(apiClient.post).mockResolvedValue({data: {}, headers: {}});
        const definition = {requirementKey: "supporting-record", leadDays: 30};

        await defineInformationRequestRefreshRule("request-a", definition, "\"request-3\"", "rule-key");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/refresh-rules",
            definition,
            {headers: {"Idempotency-Key": "rule-key", "If-Match": "\"request-3\""}},
        );

        await createInformationRequestRefresh("request-a", "rule-a", "refresh-key");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/refresh-rules/rule-a/refreshes",
            {},
            {headers: {"Idempotency-Key": "refresh-key"}},
        );
    });
});
