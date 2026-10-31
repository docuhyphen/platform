// @vitest-environment jsdom
import {act, cleanup, renderHook, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import * as outcomes from "../../../../services/informationRequestOutcomeService.ts";
import * as reviews from "../../../../services/informationRequestReviewService.ts";
import {
    FieldValueType,
    InformationRequestAcceptedFactConfidence,
    InformationRequestAcceptedFactConflictState,
    InformationRequestAcceptedFactDto,
    InformationRequestAcceptedFactFreshness,
    InformationRequestAcceptedFactVisibility,
} from "../../../models/models.tsx";
import {useRequestOutcomes} from "./useRequestOutcomes.ts";

vi.mock("../../../../services/informationRequestOutcomeService.ts", () => ({
    getInformationRequestSubmissionPackages: vi.fn(), getInformationRequestAcceptedFacts: vi.fn(),
    getInformationRequestBusinessDecisions: vi.fn(), revokeInformationRequestAcceptedFact: vi.fn(),
}));
vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({recordInformationRequestPrivacyRequest: vi.fn()}));
vi.mock("../../../../services/informationRequestReviewService.ts", () => ({listInformationRequestReviews: vi.fn()}));

const fact: InformationRequestAcceptedFactDto = {
    id: "fact-a", subjectIdentityRefId: "subject-a", purposeKey: "record.review", policyBasisKey: "policy.review",
    evidenceVersionIds: [], fieldDefinitionId: "field-a", valueType: FieldValueType.SHORT_TEXT, value: "Record A",
    sourceInformationRequestId: "request-a", sourcePackageId: "package-a", sourceSubmissionItemId: "item-a",
    sourceRequirementId: "requirement-a", visibility: InformationRequestAcceptedFactVisibility.REQUESTING_SIDE,
    confidence: InformationRequestAcceptedFactConfidence.DECLARED, validFrom: 0,
    conflictState: InformationRequestAcceptedFactConflictState.NONE, promotedAt: 0, revoked: false,
    freshness: InformationRequestAcceptedFactFreshness.CURRENT,
};

beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(outcomes.getInformationRequestSubmissionPackages).mockResolvedValue([]);
    vi.mocked(outcomes.getInformationRequestAcceptedFacts).mockResolvedValue([]);
    vi.mocked(outcomes.getInformationRequestBusinessDecisions).mockResolvedValue([]);
    vi.mocked(reviews.listInformationRequestReviews).mockResolvedValue([]);
});
afterEach(cleanup);

it("keys fact revocation by its narrative as well as its reason", async () => {
    vi.mocked(outcomes.revokeInformationRequestAcceptedFact).mockRejectedValue("Connection interrupted");
    const hook = renderHook(() => useRequestOutcomes("request-a"));
    await waitFor(() => expect(hook.result.current.loaded).toBe(true));
    const first = {reasonCode: "outdated", narrative: "Earlier details"};
    await act(async () => {
        await hook.result.current.revoke(fact, first);
    });
    await act(async () => {
        await hook.result.current.revoke(fact, first);
    });
    await act(async () => {
        await hook.result.current.revoke(fact, {...first, narrative: "Corrected details"});
    });

    const calls = vi.mocked(outcomes.revokeInformationRequestAcceptedFact).mock.calls;
    expect(calls[1][3]).toBe(calls[0][3]);
    expect(calls[2][3]).not.toBe(calls[0][3]);
});

it.each(["subject", "purpose", "policy"])("keys a correction by its %s", async changed => {
    vi.mocked(administration.recordInformationRequestPrivacyRequest).mockRejectedValue("Connection interrupted");
    const hook = renderHook(() => useRequestOutcomes("request-a"));
    await waitFor(() => expect(hook.result.current.loaded).toBe(true));
    const correction = {submissionItemId: "item-a", value: "Corrected", reasonCode: "corrected"};
    const basis = {purposeKey: "record.review", policyBasisKey: "policy.review"};
    await act(async () => {
        await hook.result.current.correct("subject-a", correction, basis);
    });
    await act(async () => {
        await hook.result.current.correct(changed === "subject" ? "subject-b" : "subject-a", correction, {
            ...basis, ...(changed === "purpose" ? {purposeKey: "record.refresh"} : {}),
            ...(changed === "policy" ? {policyBasisKey: "policy.refresh"} : {}),
        });
    });

    const calls = vi.mocked(administration.recordInformationRequestPrivacyRequest).mock.calls;
    expect(calls[1][1]).not.toBe(calls[0][1]);
});
