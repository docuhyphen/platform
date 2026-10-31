import {describe, expect, it} from "vitest";
import {
    InformationRequestCompletenessItemState,
    InformationRequestPartyDto,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestShareRoleKey,
    InformationRequestSubmissionPackageDto,
} from "../../../models/models.tsx";
import {promotionCandidates} from "./promotionEligibility.ts";

const submission = (): InformationRequestSubmissionPackageDto => ({
    id: "package-a",
    informationRequestId: "request-a",
    packageNumber: 1,
    templateVersionId: "version-a",
    contentHash: "a",
    manifestHash: "b",
    reviewRequired: false,
    completesRequest: true,
    submittedAt: Date.parse("2026-09-20T10:00:00Z"),
    submittedByCaller: false,
    withdrawn: false,
    items: [
        {
            id: "item-a",
            requirementId: "requirement-a",
            requirementKey: "contact_time",
            requirementType: InformationRequestRequirementType.FIELD,
            occurrencePath: "",
            completenessState: InformationRequestCompletenessItemState.COMPLETE,
            disposition: InformationRequestResponseDisposition.PROVIDED,
            fieldValue: "Mornings",
            fieldValueCleared: false,
            evidence: [],
        },
        {
            id: "item-b",
            requirementId: "requirement-b",
            requirementKey: "record_count",
            requirementType: InformationRequestRequirementType.FIELD,
            occurrencePath: "",
            completenessState: InformationRequestCompletenessItemState.COMPLETE,
            disposition: InformationRequestResponseDisposition.PROVIDED,
            fieldValue: 12,
            fieldValueCleared: false,
            evidence: [],
        },
    ],
    attestations: [],
    supportingEvidenceLinks: [],
    undisclosedItemCount: 0,
    current: true,
    withdrawable: false,
});

const subject = (overrides: Partial<InformationRequestPartyDto> = {}): InformationRequestPartyDto => ({
    id: "party-subject-a",
    informationRequestId: "request-a",
    roleKey: InformationRequestShareRoleKey.SUBJECT,
    active: true,
    subjectIdentityRefId: "subject-a",
    assignedAt: Date.parse("2026-09-19T10:00:00Z"),
    partyRevision: 1,
    partyETag: "\"party-subject-a-1\"",
    ...overrides,
});

const reasonsOf = (candidates: ReturnType<typeof promotionCandidates>): Record<string, string | null> =>
    Object.fromEntries(candidates.map(candidate => [candidate.answer.item.id, candidate.blockedReason]));

describe("promotionCandidates", () => {
    it("offers every answered value when the single subject is unrestricted and nothing was corrected", () => {
        expect(reasonsOf(promotionCandidates([submission()], [], [subject()], null, new Set())))
            .toEqual({"item-a": null, "item-b": null});
    });

    it("blocks every answer while the subject's processing is restricted", () => {
        expect(reasonsOf(promotionCandidates([submission()], [], [subject({processingRestricted: true})], null, new Set())))
            .toEqual({
                "item-a": "Processing for this subject is restricted, so no fact can be promoted.",
                "item-b": "Processing for this subject is restricted, so no fact can be promoted.",
            });
    });

    it("blocks only the answer whose submitted item was corrected", () => {
        expect(reasonsOf(promotionCandidates([submission()], [], [subject()], null, new Set(["item-a"]))))
            .toEqual({"item-a": "This answer was corrected, so it cannot be promoted.", "item-b": null});
    });
});
