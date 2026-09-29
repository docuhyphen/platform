// @vitest-environment jsdom
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../apiClient.ts";
import {
    getInformationRequestAcceptedFacts,
    getInformationRequestBusinessDecisions,
    getInformationRequestSubmissionPackages,
    promoteInformationRequestAcceptedFact,
    recordInformationRequestBusinessDecision,
    revokeInformationRequestAcceptedFact,
} from "../informationRequestOutcomeService.ts";
import {
    InformationRequestAcceptedFactVisibility,
    InformationRequestBusinessDecisionKind,
} from "../../app/models/models.tsx";

vi.mock("../apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn()}}));

describe("Information Request outcome transport", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(apiClient.get).mockResolvedValue({data: [{id: "read-a"}]});
        vi.mocked(apiClient.post).mockResolvedValue({data: {id: "written-a"}, headers: {}});
    });

    it("reads a request's submitted packages, promoted facts, and business decisions", async () =>
    {
        expect(await getInformationRequestSubmissionPackages("request-a")).toEqual([{id: "read-a"}]);
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/submissions");
        expect(await getInformationRequestAcceptedFacts("request-a")).toEqual([{id: "read-a"}]);
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/accepted-facts");
        expect(await getInformationRequestBusinessDecisions("request-a")).toEqual([{id: "read-a"}]);
        expect(apiClient.get).toHaveBeenLastCalledWith("/information-requests/request-a/business-decisions");
    });

    it("promotes and revokes a fact and records a decision, each under its Idempotency-Key", async () =>
    {
        const promotion = {
            packageId: "package-a",
            submissionItemId: "item-a",
            purposeKey: "reuse",
            policyBasisKey: "policy.reuse",
            visibility: InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES,
        };
        expect(await promoteInformationRequestAcceptedFact("request-a", promotion, "promote-a")).toEqual({id: "written-a"});
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/accepted-facts",
            promotion,
            {headers: {"Idempotency-Key": "promote-a"}},
        );
        await revokeInformationRequestAcceptedFact("request-a", "fact-a", {reasonCode: "CORRECTED", narrative: "Replaced"}, "revoke-a");
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/accepted-facts/fact-a/revocation",
            {reasonCode: "CORRECTED", narrative: "Replaced"},
            {headers: {"Idempotency-Key": "revoke-a"}},
        );
        const decision = {
            owningProcessKey: "intake",
            outcomeCode: "approved",
            kind: InformationRequestBusinessDecisionKind.ORIGINAL,
            decidedAt: "2026-09-27T10:00:00.000Z",
        };
        expect(await recordInformationRequestBusinessDecision("request-a", decision, "decide-a")).toEqual({id: "written-a"});
        expect(apiClient.post).toHaveBeenLastCalledWith(
            "/information-requests/request-a/business-decisions",
            decision,
            {headers: {"Idempotency-Key": "decide-a"}},
        );
    });

    it("rejects with the server's stated refusal", async () =>
    {
        const refusal = {errorMessage: "Processing for this subject is restricted", reasonCode: "INFORMATION_REQUEST_SUBJECT_RESTRICTED"};
        vi.mocked(apiClient.post).mockRejectedValue({isAxiosError: true, response: {data: refusal}, message: "Request failed"});

        await expect(promoteInformationRequestAcceptedFact(
            "request-a",
            {packageId: "package-a", submissionItemId: "item-a", purposeKey: "reuse", policyBasisKey: "policy.reuse"},
            "promote-b",
        )).rejects.toEqual(refusal);
    });
});
