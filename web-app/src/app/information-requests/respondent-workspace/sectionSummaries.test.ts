import {describe, expect, it} from "vitest";
import {
    FieldValueType,
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
    updatedAt: Date.parse("2026-09-11T00:00:00Z"),
});

describe("sectionSummaries", () =>
{
    it("counts a required Field answered only while it holds a value or an answer that needs none", () => {
        const collected = (id: string): InformationRequestTemplateRequirementDto => ({
            ...requirement(id),
            collectedFieldDefinitionId: `${id}-field`,
        });
        const valued = (bindingId: string, empty: boolean): InformationRequestResponseDto => ({
            ...response(bindingId, "root", InformationRequestResponseDisposition.PROVIDED),
            fieldValues: [{
                fieldContractId: `${bindingId}-contract`,
                namespace: "process",
                fieldKey: bindingId,
                label: bindingId,
                valueType: FieldValueType.INTEGER,
                isEmpty: empty,
                value: empty ? null : 12,
            }],
        });
        const sections: InformationRequestTemplateSectionDto[] = [{
            id: "section-a",
            sectionKey: "records",
            title: "Records",
            requirements: [collected("cleared"), collected("kept"), collected("excepted")],
        }];

        const [summary] = sectionSummaries(sections, [
            valued("cleared", true),
            valued("kept", false),
            response("excepted", "root", InformationRequestResponseDisposition.NOT_APPLICABLE),
        ]);

        expect(summary.answered).toBe(2);
        expect(summary.required).toBe(3);
    });

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
            {
                id: "section-a",
                title: "Records",
                answered: 1,
                required: 2,
                confirmations: 0,
                occurrencePath: "root",
                requirementKey: "count-key",
                requirementId: "runtime-count-root",
            },
            {
                id: "section-b",
                title: "Entries",
                answered: 1,
                required: 2,
                confirmations: 0,
                occurrencePath: "items[0]",
                requirementKey: "entry-key",
                requirementId: "runtime-entry-items[0]",
            },
        ]);
    });

    it("leaves a confirmation out of the answers it counts and points a section that opens with one at its card", () => {
        const confirmation = (id: string): InformationRequestTemplateRequirementDto => ({
            ...requirement(id),
            requirementType: InformationRequestRequirementType.RESPONSE_ATTESTATION,
        });
        const sections: InformationRequestTemplateSectionDto[] = [
            {
                id: "section-a",
                sectionKey: "records",
                title: "Records",
                requirements: [requirement("count"), confirmation("confirm-records")]
            },
            {
                id: "section-b",
                sectionKey: "confirmation",
                title: "Confirmation",
                requirements: [confirmation("confirm-all")]
            },
        ];

        const [records, confirmationOnly] = sectionSummaries(sections, [
            response("count", "root", InformationRequestResponseDisposition.PROVIDED),
            response("confirm-records", "root", InformationRequestResponseDisposition.NOT_ANSWERED),
            response("confirm-all", "root", InformationRequestResponseDisposition.NOT_ANSWERED),
        ]);

        expect([records.answered, records.required, records.confirmations]).toEqual([1, 1, 1]);
        expect(records.requirementKey).toBe("count-key");
        expect([confirmationOnly.answered, confirmationOnly.required, confirmationOnly.confirmations]).toEqual([0, 0, 1]);
        expect(confirmationOnly.requirementId).toBe("runtime-confirm-all-root");
    });
});
