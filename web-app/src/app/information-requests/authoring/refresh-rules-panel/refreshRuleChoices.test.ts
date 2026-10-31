import {describe, expect, it} from "vitest";
import {
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestRequirementType,
    InformationRequestTemplateEvidencePolicyDto,
    InformationRequestTemplateRequirementDto,
    InformationRequestTemplateSectionDto,
} from "../../../models/models.tsx";
import {refreshableDocuments} from "./refreshRuleChoices.ts";

const documentRequirement = (
    requirementKey: string,
    evidencePolicy: Partial<InformationRequestTemplateEvidencePolicyDto> | null,
): InformationRequestTemplateRequirementDto => ({
    templateRequirementId: `requirement-${requirementKey}`,
    requirementKey,
    requirementType: InformationRequestRequirementType.DOCUMENT,
    prompt: `Attach the ${requirementKey}`,
    evidencePolicy: evidencePolicy as InformationRequestTemplateEvidencePolicyDto | null,
} as InformationRequestTemplateRequirementDto);

const sections = (requirements: InformationRequestTemplateRequirementDto[]): InformationRequestTemplateSectionDto[] =>
    [{sectionKey: "files", title: "Files", requirements} as unknown as InformationRequestTemplateSectionDto];

describe("refreshableDocuments", () => {
    it("offers only documents whose evidence policy captures an expiry, also when the server sends a policy as null", () => {
        const choices = refreshableDocuments(sections([
            documentRequirement("dated-record", {expiryDateRequirement: InformationRequestEvidenceAttributeRequirement.REQUIRED}),
            documentRequirement("undated-record", {expiryDateRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED}),
            documentRequirement("unpoliced-record", null),
        ]));

        expect(choices).toEqual([{requirementKey: "dated-record", label: "Attach the dated-record"}]);
    });
});
