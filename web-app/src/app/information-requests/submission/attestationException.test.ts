import {describe, expect, it} from "vitest";
import {
    InformationRequestContributorRole,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestTemplateRequirementDto,
} from "../../models/models.tsx";
import {attestationExceptionsOf} from "./attestationException.ts";

const requirement = (overrides: Partial<InformationRequestTemplateRequirementDto> = {}): InformationRequestTemplateRequirementDto => ({
    id: "binding-confirm",
    templateRequirementId: "requirement-confirm",
    requirementKey: "recorded-assertion",
    requirementType: InformationRequestRequirementType.RESPONSE_ATTESTATION,
    prompt: "Confirm the recorded items are accurate",
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.ATTESTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    permittedDispositions: [
        InformationRequestResponseDisposition.PROVIDED,
        InformationRequestResponseDisposition.NOT_APPLICABLE,
        InformationRequestResponseDisposition.UNAVAILABLE,
    ],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
    ...overrides,
});

const response = (overrides: Partial<InformationRequestResponseDto> = {}): InformationRequestResponseDto => ({
    informationRequestRequirementId: "assertion-a",
    sourceTemplateRequirementId: "requirement-confirm",
    sourceTemplateBindingId: "binding-confirm",
    occurrencePath: "root",
    disposition: InformationRequestResponseDisposition.NOT_ANSWERED,
    fieldValues: [],
    responseRevision: 1,
    updatedAt: 0,
    ...overrides,
});

describe("attestationExceptionsOf", () => {
    it("offers a confirmation's permitted exceptions by its runtime requirement, never a provided or partial answer", () => {
        const settings = attestationExceptionsOf([requirement()], [response()]);

        expect(settings["assertion-a"]).toEqual({
            permitted: [InformationRequestResponseDisposition.NOT_APPLICABLE, InformationRequestResponseDisposition.UNAVAILABLE],
            current: null,
        });
    });

    it("states the exception already recorded with its reason", () => {
        const settings = attestationExceptionsOf([requirement()], [response({
            disposition: InformationRequestResponseDisposition.UNAVAILABLE,
            narrative: "The register was lost in the move",
        })]);

        expect(settings["assertion-a"].current).toEqual({
            disposition: InformationRequestResponseDisposition.UNAVAILABLE,
            reason: "The register was lost in the move",
        });
    });

    it("lists nothing for a confirmation that permits no exception or for an ordinary answer", () => {
        const settings = attestationExceptionsOf([
            requirement({permittedDispositions: [InformationRequestResponseDisposition.PROVIDED]}),
            requirement({id: "binding-count", requirementType: InformationRequestRequirementType.FIELD}),
        ], [response(), response({informationRequestRequirementId: "count-a", sourceTemplateBindingId: "binding-count"})]);

        expect(settings).toEqual({});
    });
});
