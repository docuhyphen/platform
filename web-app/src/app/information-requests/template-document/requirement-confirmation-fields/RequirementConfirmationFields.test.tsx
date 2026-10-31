/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestAttestationOrdering,
    InformationRequestContributorRole,
    InformationRequestRequirementType,
    InformationRequestResponseMode,
    InformationRequestTemplateAttestationPolicyRequest,
} from "../../../models/models.tsx";
import {RequirementDraft, withRequirementType} from "../template-requirement-dialog/requirementDraft.ts";
import RequirementConfirmationFields from "./RequirementConfirmationFields.tsx";

const draft = (policy: InformationRequestTemplateAttestationPolicyRequest): RequirementDraft => ({
    requirementKey: "recorded-assertion",
    requirementType: InformationRequestRequirementType.RESPONSE_ATTESTATION,
    prompt: "Confirm the recorded answers",
    responseMode: InformationRequestResponseMode.PROVIDE,
    attestationPolicy: policy,
});

const renderFields = (policy: InformationRequestTemplateAttestationPolicyRequest) => {
    const onChange = vi.fn();
    render(
        <RequirementConfirmationFields id={"requirement"}
                                       draft={draft(policy)}
                                       readOnly={false}
                                       onChange={onChange}/>,
    );
    return onChange;
};

const changedPolicy = (onChange: ReturnType<typeof vi.fn>) =>
    (onChange.mock.calls[0][0] as Partial<RequirementDraft>).attestationPolicy;

describe("confirmation roles keep the order the author states", () => {
    afterEach(cleanup);

    it("ticking a role adds it after the roles already stated", () => {
        const onChange = renderFields({
            requiredRoles: [InformationRequestContributorRole.ATTESTOR, InformationRequestContributorRole.CONTRIBUTOR],
        });

        fireEvent.click(screen.getByRole("checkbox", {name: "Preparer"}));

        expect(changedPolicy(onChange)?.requiredRoles).toEqual([
            InformationRequestContributorRole.ATTESTOR,
            InformationRequestContributorRole.CONTRIBUTOR,
            InformationRequestContributorRole.PREPARER,
        ]);
    });

    it("in the order of the roles, a role can move before another", () => {
        const onChange = renderFields({
            requiredRoles: [InformationRequestContributorRole.CONTRIBUTOR, InformationRequestContributorRole.PREPARER],
            ordering: InformationRequestAttestationOrdering.ROLE_SEQUENCE,
        });

        fireEvent.click(screen.getByRole("button", {name: "Move Preparer earlier"}));

        expect(changedPolicy(onChange)?.requiredRoles).toEqual([
            InformationRequestContributorRole.PREPARER,
            InformationRequestContributorRole.CONTRIBUTOR,
        ]);
    });

    it("a minimum that needed one confirmation from each role follows a newly ticked role", () => {
        const onChange = renderFields({
            requiredRoles: [InformationRequestContributorRole.CONTRIBUTOR],
            minimumAssentCount: 1,
        });

        fireEvent.click(screen.getByRole("checkbox", {name: "Preparer"}));

        expect(changedPolicy(onChange)?.minimumAssentCount).toBe(2);
    });

    it("a minimum the author set below the number of roles stays as set", () => {
        const onChange = renderFields({
            requiredRoles: [InformationRequestContributorRole.CONTRIBUTOR, InformationRequestContributorRole.PREPARER],
            minimumAssentCount: 1,
        });

        fireEvent.click(screen.getByRole("checkbox", {name: "Attestor"}));

        expect(changedPolicy(onChange)?.minimumAssentCount).toBe(1);
    });
});

describe("changing what a requirement asks for", () => {
    it("drops supporting documents when a requirement becomes a document", () => {
        const changed = withRequirementType({
            requirementKey: "recorded-note",
            requirementType: InformationRequestRequirementType.FIELD,
            prompt: "State the recorded note",
            supportingEvidenceRequirementKeys: ["supporting-record"],
        }, InformationRequestRequirementType.DOCUMENT);

        expect(changed.supportingEvidenceRequirementKeys).toEqual([]);
    });
});
