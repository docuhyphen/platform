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
    InformationRequestTemplateSectionDto,
} from "../../models/models.tsx";
import {sectionSummaries} from "./sectionSummaries.ts";

const requirement = (id: string, requiredness = InformationRequestRequiredness.REQUIRED): InformationRequestTemplateRequirementDto => ({
    id,
    templateRequirementId: id,
    requirementKey: `${id}-key`,
    requirementType: InformationRequestRequirementType.FIELD,
    prompt: id,
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
});

const response = (bindingId: string, occurrencePath: string, disposition: InformationRequestResponseDisposition): InformationRequestResponseDto => ({
    informationRequestRequirementId: `runtime-${bindingId}-${occurrencePath}`,
    sourceTemplateRequirementId: bindingId,
    sourceTemplateBindingId: bindingId,
    occurrencePath,
    disposition,
    fieldValues: [],
    responseRevision: 1,
    updatedAt: "2026-09-11T00:00:00Z",
});

describe("sectionSummaries", () =>
{
    it("counts required answers per section and points at the section's first item", () =>
    {
        const sections: InformationRequestTemplateSectionDto[] = [
            {id: "section-a", sectionKey: "records", title: "Records", requirements: [requirement("count"), requirement("keeper"), requirement("note", InformationRequestRequiredness.OPTIONAL)]},
            {id: "section-b", sectionKey: "entries", title: "Entries", requirements: [requirement("entry")]},
        ];

        const summaries = sectionSummaries(sections, [
            response("count", "root", InformationRequestResponseDisposition.PROVIDED),
            response("keeper", "root", InformationRequestResponseDisposition.NOT_ANSWERED),
            response("note", "root", InformationRequestResponseDisposition.NOT_ANSWERED),
            response("entry", "items[0]", InformationRequestResponseDisposition.NOT_APPLICABLE),
            response("entry", "items[1]", InformationRequestResponseDisposition.NOT_ANSWERED),
        ]);

        expect(summaries).toEqual([
            {id: "section-a", title: "Records", answered: 1, required: 2, occurrencePath: "root", requirementKey: "count-key"},
            {id: "section-b", title: "Entries", answered: 1, required: 2, occurrencePath: "items[0]", requirementKey: "entry-key"},
        ]);
    });
});
