/** @vitest-environment jsdom */
import {cleanup, render} from "@testing-library/react";
import {afterEach, describe, expect, it} from "vitest";
import {
    InformationRequestContributorRole,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateSectionDto,
} from "../../../models/models.tsx";
import RecipientPreviewSections from "./RecipientPreviewSections.tsx";

const requirement: InformationRequestTemplateRequirementDto = {
    id: "binding-a",
    templateRequirementId: "template-requirement-a",
    requirementKey: "supporting-record",
    requirementType: InformationRequestRequirementType.DOCUMENT,
    prompt: "Provide the supporting record",
    helpText: "A scanned copy is enough",
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
};

const sectionWith = (requirements: InformationRequestTemplateRequirementDto[]): InformationRequestTemplateSectionDto[] => [{
    id: "section-a",
    sectionKey: "intake",
    title: "Intake",
    helpText: "Answer what you can",
    requirements,
}];

describe("RecipientPreviewSections", () => {
    afterEach(cleanup);

    it("gives the section help and every line of a requirement an id built from its section and requirement", () => {
        const {container} = render(<RecipientPreviewSections sections={sectionWith([requirement])}/>);
        const text = (id: string) => container.querySelector(`#${id}`)?.textContent;

        expect(text("information-request-preview-section-section-a-help")).toBe("Answer what you can");
        expect(text("information-request-preview-requirement-binding-a-prompt-text")).toBe("Provide the supporting record");
        expect(text("information-request-preview-requirement-binding-a-requiredness")).toBe("Required");
        expect(text("information-request-preview-requirement-binding-a-help")).toBe("A scanned copy is enough");
        expect(text("information-request-preview-requirement-binding-a-answer-kind")).toBe("Answered by uploading files");
        expect(container.querySelector("#information-request-preview-requirement-binding-a-condition")).toBeNull();
        expect(container.querySelector("#information-request-preview-requirement-binding-a-protected")).toBeNull();
    });

    it("says when a question depends on earlier answers and when it waits for the party to confirm it is them", () => {
        const {container} = render(<RecipientPreviewSections sections={sectionWith([{
            ...requirement,
            requiredness: InformationRequestRequiredness.CONDITIONAL,
            conditionalRuleKey: "detail-rule",
            confidentialityCompartmentKey: "restricted-records",
        }])}/>);
        const text = (id: string) => container.querySelector(`#${id}`)?.textContent;

        expect(text("information-request-preview-requirement-binding-a-requiredness")).toBe("Required when it applies");
        expect(text("information-request-preview-requirement-binding-a-condition"))
            .toBe("Asked only when their earlier answers call for it.");
        expect(text("information-request-preview-requirement-binding-a-protected"))
            .toBe("Shown only after they confirm it is them again.");
        expect(container.textContent).not.toContain("detail-rule");
        expect(container.textContent).not.toContain("restricted-records");
    });
});
