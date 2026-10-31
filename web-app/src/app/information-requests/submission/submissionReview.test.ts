import {describe, expect, it} from "vitest";
import {
    FieldDataClassification,
    FieldValueType,
    InformationRequestContributorRole,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseDto,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestTemplateRequirementDto,
    SchemaFieldBindingDto,
} from "../../models/models.tsx";
import {submissionReviewItems, SubmissionReviewScope} from "./submissionReview.ts";

const requirement = (id: string, prompt: string, requirementType: InformationRequestRequirementType): InformationRequestTemplateRequirementDto => ({
    id,
    templateRequirementId: id,
    requirementKey: id,
    requirementType,
    prompt,
    responseMode: InformationRequestResponseMode.PROVIDE,
    requiredness: InformationRequestRequiredness.REQUIRED,
    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
    substituteRequirementKeys: [],
    supportingEvidenceRequirementKeys: [],
});

const whole = (requirements: InformationRequestTemplateRequirementDto[]): SubmissionReviewScope => ({
    sections: [{id: "section-a", sectionKey: "records", title: "Records", requirements}],
});

const typed = (value: string): InformationRequestResponseDto["fieldValues"] => [{
    fieldContractId: "contract-a",
    schemaFieldBindingId: "binding-a",
    namespace: "process",
    fieldKey: "count",
    label: "Count",
    valueType: FieldValueType.SHORT_TEXT,
    isEmpty: false,
    value,
}];

const response = (bindingId: string, disposition: InformationRequestResponseDisposition, overrides: Partial<InformationRequestResponseDto> = {}): InformationRequestResponseDto => ({
    informationRequestRequirementId: `runtime-${bindingId}`,
    sourceTemplateRequirementId: bindingId,
    sourceTemplateBindingId: bindingId,
    occurrencePath: "root",
    disposition,
    fieldValues: [],
    responseRevision: 1,
    updatedAt: Date.parse("2026-09-11T00:00:00Z"),
    ...overrides,
});

describe("submissionReviewItems", () =>
{
    it("distinguishes repeated entries by their occurrence in the review summary", () => {
        const items = submissionReviewItems([
            response("record", InformationRequestResponseDisposition.PROVIDED, {occurrencePath: "records[0]"}),
            response("record", InformationRequestResponseDisposition.PROVIDED, {
                informationRequestRequirementId: "runtime-second",
                occurrencePath: "records[1]"
            }),
        ], whole([requirement("record", "Supporting record", InformationRequestRequirementType.DOCUMENT)]));
        expect(items.map(item => item.label)).toEqual(["Supporting record (Records 1)", "Supporting record (Records 2)"]);
    });
    it("states each answer in words and leaves confirmations to their own step", () =>
    {
        const items = submissionReviewItems(
            [
                response("count", InformationRequestResponseDisposition.PROVIDED, {
                    fieldValues: [{
                        fieldContractId: "contract-a",
                        schemaFieldBindingId: "binding-a",
                        namespace: "process",
                        fieldKey: "count",
                        label: "Count",
                        valueType: FieldValueType.SHORT_TEXT,
                        isEmpty: false,
                        value: "12",
                    }],
                }),
                response("register", InformationRequestResponseDisposition.NOT_APPLICABLE, {narrative: "No register this period"}),
                response("files", InformationRequestResponseDisposition.PROVIDED, {evidenceFileCount: 2}),
                response("pending", InformationRequestResponseDisposition.NOT_ANSWERED),
                response("assent", InformationRequestResponseDisposition.PROVIDED),
            ],
            whole([
                requirement("count", "How many records were kept?", InformationRequestRequirementType.FIELD),
                requirement("register", "Attach the record register", InformationRequestRequirementType.DOCUMENT),
                requirement("files", "Attach the supporting files", InformationRequestRequirementType.DOCUMENT),
                requirement("pending", "Name the keeper", InformationRequestRequirementType.FIELD),
                requirement("assent", "Confirm the response", InformationRequestRequirementType.RESPONSE_ATTESTATION),
            ]),
        );

        expect(items).toEqual([
            {requirementId: "runtime-count", label: "How many records were kept?", answer: "12"},
            {requirementId: "runtime-register", label: "Attach the record register", answer: "Not applicable: No register this period"},
            {requirementId: "runtime-files", label: "Attach the supporting files", answer: "2 files attached"},
            {requirementId: "runtime-pending", label: "Name the keeper", answer: "Not answered yet"},
        ]);
    });

    it("states a partly provided answer with its value or files and its reason", () => {
        const items = submissionReviewItems(
            [
                response("count", InformationRequestResponseDisposition.PARTIALLY_PROVIDED, {
                    fieldValues: typed("Three records"),
                    narrative: "Two records remain unavailable",
                }),
                response("files", InformationRequestResponseDisposition.PARTIALLY_PROVIDED, {
                    narrative: "One file follows",
                    evidenceFileCount: 1,
                }),
                response("keeper", InformationRequestResponseDisposition.PARTIALLY_PROVIDED, {narrative: "No keeper is named yet"}),
            ],
            whole([
                requirement("count", "How many records were kept?", InformationRequestRequirementType.FIELD),
                requirement("files", "Attach the supporting files", InformationRequestRequirementType.DOCUMENT),
                requirement("keeper", "Name the keeper", InformationRequestRequirementType.FIELD),
            ]),
        );

        expect(items.map(item => item.answer)).toEqual([
            "Partly provided: Three records. Reason: Two records remain unavailable",
            "Partly provided: 1 file attached. Reason: One file follows",
            "Partly provided. Reason: No keeper is named yet",
        ]);
    });

    it("states the note given with a provided answer", () => {
        const items = submissionReviewItems(
            [
                response("count", InformationRequestResponseDisposition.PROVIDED, {
                    fieldValues: typed("12"),
                    narrative: "Counted by hand",
                }),
                response("files", InformationRequestResponseDisposition.PROVIDED, {
                    narrative: "Scanned copies",
                    evidenceFileCount: 3
                }),
            ],
            whole([
                requirement("count", "How many records were kept?", InformationRequestRequirementType.FIELD),
                requirement("files", "Attach the supporting files", InformationRequestRequirementType.DOCUMENT),
            ]),
        );

        expect(items.map(item => item.answer)).toEqual([
            "12. Note: Counted by hand",
            "3 files attached. Note: Scanned copies",
        ]);
    });

    it("states a Document answer from the files actually attached rather than from its disposition", () => {
        const document = (key: string) => requirement(key, `Attach ${key}`, InformationRequestRequirementType.DOCUMENT);
        const items = submissionReviewItems(
            [
                response("claimed", InformationRequestResponseDisposition.PROVIDED, {evidenceFileCount: 0}),
                response("uncounted", InformationRequestResponseDisposition.PROVIDED),
                response("unmarked", InformationRequestResponseDisposition.NOT_ANSWERED, {evidenceFileCount: 1}),
                response("empty", InformationRequestResponseDisposition.NOT_ANSWERED, {evidenceFileCount: 0}),
                response("partial", InformationRequestResponseDisposition.PARTIALLY_PROVIDED, {
                    evidenceFileCount: 0,
                    narrative: "The file follows later",
                }),
                response("withdrawn", InformationRequestResponseDisposition.PROVIDED, {
                    evidenceFileCount: 0,
                    narrative: "Replaced"
                }),
                response("absent", InformationRequestResponseDisposition.UNAVAILABLE, {
                    evidenceFileCount: 0,
                    narrative: "The record was never kept",
                }),
            ],
            whole(["claimed", "uncounted", "unmarked", "empty", "partial", "withdrawn", "absent"].map(document)),
        );

        expect(items.map(item => item.answer)).toEqual([
            "No files attached yet",
            "No files attached yet",
            "1 file attached",
            "Not answered yet",
            "Partly provided. Reason: The file follows later",
            "No files attached yet",
            "Unavailable: The record was never kept",
        ]);
    });

    it("lists only the answers of the part being submitted", () => {
        const scope = (stageKey: string | null): SubmissionReviewScope => ({
            stageKey,
            sections: [
                {
                    id: "section-a",
                    sectionKey: "records",
                    title: "Records",
                    submissionStageKey: "first",
                    requirements: [
                        requirement("count", "How many records were kept?", InformationRequestRequirementType.FIELD),
                    ]
                },
                {
                    id: "section-b",
                    sectionKey: "later",
                    title: "Later records",
                    submissionStageKey: "second",
                    requirements: [
                        requirement("files", "Attach the supporting files", InformationRequestRequirementType.DOCUMENT),
                        requirement("keeper", "Name the keeper", InformationRequestRequirementType.FIELD),
                    ]
                },
            ],
        });
        const responses = [
            response("count", InformationRequestResponseDisposition.PROVIDED, {fieldValues: typed("12")}),
            response("files", InformationRequestResponseDisposition.PROVIDED),
            response("keeper", InformationRequestResponseDisposition.NOT_ANSWERED),
            response("unplaced", InformationRequestResponseDisposition.PROVIDED),
        ];

        expect(submissionReviewItems(responses, scope("first")).map(item => item.requirementId)).toEqual(["runtime-count"]);
        expect(submissionReviewItems(responses, scope("second")).map(item => item.requirementId)).toEqual(["runtime-files", "runtime-keeper"]);
        expect(submissionReviewItems(responses, scope(null)).map(item => item.requirementId))
            .toEqual(["runtime-count", "runtime-files", "runtime-keeper", "runtime-unplaced"]);
    });

    it("states a choice by the label its collected Field binding gives, including a retired choice, and an unknown code as itself", () => {
        const choiceBinding = (valueType: FieldValueType): SchemaFieldBindingDto => ({
            id: `binding-${valueType}`,
            fieldContractId: `contract-${valueType}`,
            fieldDefinitionId: `definition-${valueType}`,
            namespace: "process",
            fieldKey: "keeper",
            label: "Keeper",
            valueType,
            displayOrder: 1,
            isRequired: false,
            isReadOnly: false,
            visibility: FieldDataClassification.INTERNAL,
            constraints: {},
            options: [
                {code: "office-code", label: "Records office"},
                {code: "archive-code", label: "Former archive", active: false},
            ],
        });
        const bindings = [choiceBinding(FieldValueType.SINGLE_SELECT), choiceBinding(FieldValueType.MULTI_SELECT)];
        const choice = (key: string, valueType: FieldValueType, value: string | string[]) =>
            response(key, InformationRequestResponseDisposition.PROVIDED, {
                fieldValues: [{
                    fieldContractId: `contract-${valueType}`,
                    schemaFieldBindingId: `binding-${valueType}`,
                    namespace: "process",
                    fieldKey: "keeper",
                    label: "Keeper",
                    valueType,
                    isEmpty: false,
                    value,
                }],
            });
        const asked = (key: string, valueType: FieldValueType): InformationRequestTemplateRequirementDto => ({
            ...requirement(key, "Who keeps the records?", InformationRequestRequirementType.FIELD),
            collectedFieldDefinitionId: `definition-${valueType}`,
        });

        const items = submissionReviewItems(
            [
                choice("single", FieldValueType.SINGLE_SELECT, "office-code"),
                choice("retired", FieldValueType.SINGLE_SELECT, "archive-code"),
                choice("unknown", FieldValueType.SINGLE_SELECT, "missing-code"),
                choice("several", FieldValueType.MULTI_SELECT, ["office-code", "archive-code", "missing-code"]),
            ],
            {
                ...whole([
                    asked("single", FieldValueType.SINGLE_SELECT),
                    asked("retired", FieldValueType.SINGLE_SELECT),
                    asked("unknown", FieldValueType.SINGLE_SELECT),
                    asked("several", FieldValueType.MULTI_SELECT),
                ]),
                bindings,
            },
        );

        expect(items.map(item => item.answer)).toEqual([
            "Records office",
            "Former archive",
            "missing-code",
            "Records office, Former archive, missing-code",
        ]);
    });
});
