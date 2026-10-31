import {describe, expect, it} from "vitest";
import {
    FieldOperator,
    FieldValueType,
    InformationRequestConditionHiddenDataPolicy,
    InformationRequestContributorRole,
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestEvidenceWaiverPolicy,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestReviewAggregation,
    InformationRequestReviewPolicy,
    InformationRequestReviewTieResolution,
    InformationRequestSubmissionMode,
    InformationRequestTemplateEvidencePolicyRequest,
} from "../../models/models.tsx";
import {emptyDraftDocument, newRequirement, TemplateDraftDocument} from "./templateDraftDocument.ts";
import {targetForRefusal, templateDraftProblems} from "./templateDraftValidation.ts";

const typed = (key: string, fieldId = `field-${key}`) => ({
    ...newRequirement(InformationRequestRequirementType.FIELD, key, `Provide ${key}`),
    collectedFieldDefinitionId: fieldId,
});

const documentWith = (overrides: Partial<TemplateDraftDocument>): TemplateDraftDocument => ({
    ...emptyDraftDocument(),
    schemaVersionId: "schema-version-1",
    sections: [{sectionKey: "collected-data", title: "Collected data", requirements: [typed("recorded-note")]}],
    ...overrides,
});

const messages = (document: TemplateDraftDocument, fields?: ReadonlySet<string>) =>
    templateDraftProblems(document, fields).map(problem => problem.message);

const groupedBy = (group: TemplateDraftDocument["groups"][number]) => documentWith({
    sections: [{
        sectionKey: "collected-data",
        title: "Collected data",
        requirements: [{...typed("recorded-note"), occurrenceAnchorKey: group.groupKey}],
    }],
    groups: [group],
});

describe("templateDraftValidation", () =>
{
    it("points a subject contributor refusal at the affected Requirement", () => {
        const problems = templateDraftProblems(documentWith({
            sections: [{
                sectionKey: "collected-data",
                title: "Collected data",
                requirements: [{...typed("recorded-note"), contributorRole: InformationRequestContributorRole.SUBJECT}],
            }],
        }));

        expect(problems).toEqual([{
            key: "problem-0",
            message: 'Requirement "Provide recorded-note" needs an acting role. Subject identifies who or what the request is about.',
            target: {panel: "sections", sectionIndex: 0, requirementIndex: 0},
        }]);
    });

    it.each([false, true])("refuses Subject in confirmation roles, mixed: %s", mixed => {
        const roles = mixed
            ? [InformationRequestContributorRole.ATTESTOR, InformationRequestContributorRole.SUBJECT]
            : [InformationRequestContributorRole.SUBJECT];
        const requirement = {
            ...newRequirement(InformationRequestRequirementType.RESPONSE_ATTESTATION, "recorded-assertion", "Confirm the records"),
            attestationPolicy: {requiredRoles: roles},
        };

        expect(messages(documentWith({
            sections: [{sectionKey: "confirmations", title: "Confirmations", requirements: [requirement]}],
        }))).toEqual(['Requirement "Confirm the records" needs acting confirmation roles. Subject cannot confirm information.']);
    });

    it("accepts a complete document", () =>
    {
        expect(templateDraftProblems(documentWith({}), new Set(["field-recorded-note"]))).toEqual([]);
    });

    it("asks for a first section and for sections that ask for something", () =>
    {
        expect(messages(documentWith({sections: []}))).toEqual(["Add at least one section."]);
        expect(messages(documentWith({
            sections: [{sectionKey: "Bad Key", title: " ", requirements: []}],
        }))).toEqual([
            "Section key \"Bad Key\" must use lowercase letters, digits, and inner hyphens.",
            "Section \"Bad Key\" needs a title.",
            "Section \"Bad Key\" asks for nothing yet.",
        ]);
    });

    it("names duplicate keys, missing prompts, and typed answers that cannot be recorded", () =>
    {
        const problems = templateDraftProblems(documentWith({
            schemaVersionId: undefined,
            sections: [{
                sectionKey: "collected-data",
                title: "Collected data",
                requirements: [
                    {...typed("recorded-note"), prompt: ""},
                    typed("recorded-note", "field-other"),
                    {...typed("other-note"), collectedFieldDefinitionId: undefined},
                    typed("third-note", "field-recorded-note"),
                ],
            }],
        }), new Set(["field-recorded-note", "field-other"]));

        expect(problems.map(problem => problem.message)).toEqual([
            "Choose the Request Schema that typed answers are recorded against.",
            "Requirement \"recorded-note\" needs a prompt.",
            "Two requirements use the key \"recorded-note\".",
            "Requirement \"Provide other-note\" needs the Field it collects.",
            "Requirements \"recorded-note\" and \"Provide third-note\" collect the same Field.",
        ]);
        expect(problems[1].target).toEqual({panel: "sections", sectionIndex: 0, requirementIndex: 0});
        expect(problems[0].target).toEqual({panel: "settings"});
    });

    it("refuses a Field the chosen Schema Version does not ask for", () =>
    {
        expect(messages(documentWith({}), new Set(["field-other"])))
            .toEqual(["Requirement \"Provide recorded-note\" collects a Field the chosen Request Schema does not ask for."]);
    });

    it("refuses a Field its Schema marks read-only unless the party only views it", () => {
        const readOnly = new Set(["field-recorded-note"]);
        const viewed = documentWith({
            sections: [{
                sectionKey: "collected-data",
                title: "Collected data",
                requirements: [{
                    ...typed("recorded-note"),
                    responseMode: InformationRequestResponseMode.VIEW_ONLY,
                    requiredness: InformationRequestRequiredness.OPTIONAL,
                    permittedDispositions: [],
                }],
            }],
        });

        expect(templateDraftProblems(documentWith({}), new Set(["field-recorded-note"]), readOnly).map(problem => problem.message))
            .toEqual(["Requirement \"Provide recorded-note\" collects a Field its Request Schema marks read-only, so nobody could answer it."]);
        expect(templateDraftProblems(viewed, new Set(["field-recorded-note"]), readOnly)).toEqual([]);
    });

    it("keeps conditions, groups, and waivers consistent with the rest of the document", () =>
    {
        const file = {
            ...newRequirement(InformationRequestRequirementType.DOCUMENT, "supporting-file", "Attach the file"),
            requiredness: InformationRequestRequiredness.CONDITIONAL,
            occurrenceAnchorKey: "entries",
        };
        const waived = {
            ...newRequirement(InformationRequestRequirementType.DOCUMENT, "other-file", "Attach the other file"),
            evidencePolicy: {
                ...newRequirement(InformationRequestRequirementType.DOCUMENT, "x", "x").evidencePolicy,
                waiverPolicy: InformationRequestEvidenceWaiverPolicy.RESPONDENT_DECLARED,
                minimumFileCount: 3,
                maximumFileCount: 2,
            },
        };

        expect(messages(documentWith({
            sections: [{sectionKey: "files", title: "Files", requirements: [file, waived]}],
        }))).toEqual([
            "Requirement \"Attach the file\" applies only sometimes, so choose the condition that decides it.",
            "Requirement \"Attach the file\" repeats per \"entries\", which is not a group of this Template.",
            "Requirement \"Attach the other file\" needs at least as many files allowed as it requires.",
            "Requirement \"Attach the other file\" states a waiver rule, so it permits a waived answer.",
        ]);
    });

    it("refuses the evidence bounds the server refuses", () => {
        const required = InformationRequestEvidenceAttributeRequirement.REQUIRED;
        const file = (key: string, bounds: Partial<InformationRequestTemplateEvidencePolicyRequest>) => {
            const requirement = newRequirement(InformationRequestRequirementType.DOCUMENT, key, `Attach ${key}`);
            return {...requirement, evidencePolicy: {...requirement.evidencePolicy, ...bounds}};
        };

        expect(messages(documentWith({
            sections: [{
                sectionKey: "files",
                title: "Files",
                requirements: [
                    file("no-files", {minimumFileCount: 0}),
                    file("small-total", {maximumFileSizeBytes: 2048, maximumTotalSizeBytes: 1024}),
                    file("empty-total", {maximumTotalSizeBytes: 0}),
                    file("no-pages", {minimumPageCount: 0}),
                    file("issue-age", {maximumIssueAgeDays: 30}),
                    file("short-age", {maximumIssueAgeDays: 0, issueDateRequirement: required}),
                    file("validity", {minimumRemainingValidityDays: 10}),
                    file("negative-validity", {minimumRemainingValidityDays: -1, expiryDateRequirement: required}),
                    file("continuity", {coverageContinuityRequired: true}),
                    file("short-coverage", {minimumCoverageDays: 0, coveragePeriodRequirement: required}),
                    file("bounded", {
                        maximumFileSizeBytes: 1024,
                        maximumTotalSizeBytes: 1024,
                        minimumPageCount: 1,
                        maximumIssueAgeDays: 30,
                        issueDateRequirement: required,
                        minimumRemainingValidityDays: 0,
                        expiryDateRequirement: required,
                        minimumCoverageDays: 30,
                        coverageContinuityRequired: true,
                        coveragePeriodRequirement: required,
                    }),
                ],
            }],
        }))).toEqual([
            "Requirement \"Attach no-files\" needs at least one file.",
            "Requirement \"Attach small-total\" needs a total size limit at least as large as its file size limit.",
            "Requirement \"Attach empty-total\" needs a positive total size limit.",
            "Requirement \"Attach no-pages\" needs a minimum of at least one page.",
            "Requirement \"Attach issue-age\" limits the age since issue, so set its Issue date to Required.",
            "Requirement \"Attach short-age\" needs a maximum age since issue of at least one day.",
            "Requirement \"Attach validity\" asks for validity remaining, so set its Expiry date to Required.",
            "Requirement \"Attach negative-validity\" needs a minimum validity remaining of zero days or more.",
            "Requirement \"Attach continuity\" sets coverage rules, so set its Coverage period to Required.",
            "Requirement \"Attach short-coverage\" needs a minimum coverage of at least one day.",
        ]);
    });

    it("keeps answers owed only by a party that can answer", () =>
    {
        const viewOnly = {
            ...typed("recorded-note"),
            responseMode: InformationRequestResponseMode.VIEW_ONLY,
        };
        const exceptional = {
            ...typed("other-note"),
            reviewPolicy: InformationRequestReviewPolicy.REQUIRED_ON_EXCEPTION,
        };

        expect(messages(documentWith({
            sections: [{sectionKey: "collected-data", title: "Collected data", requirements: [viewOnly, exceptional]}],
        }))).toEqual([
            "Requirement \"Provide recorded-note\" cannot be answered by its party, so make it optional.",
            "Requirement \"Provide recorded-note\" cannot be answered by its party, so it offers no answers to choose from.",
            "Requirement \"Provide other-note\" is reviewed only on an exception, so it permits an answer that is one.",
        ]);
    });

    it("checks staged sections, condition rules, groups, and review stages", () =>
    {
        const conditional = {
            ...typed("other-note"),
            requiredness: InformationRequestRequiredness.CONDITIONAL,
            conditionalRuleKey: "when-note",
            reviewPolicy: InformationRequestReviewPolicy.REQUIRED,
        };

        const problems = templateDraftProblems(documentWith({
            submissionMode: InformationRequestSubmissionMode.STAGED,
            sections: [
                {sectionKey: "collected-data", title: "Collected data", requirements: [typed("recorded-note")]},
                {
                    sectionKey: "follow-up",
                    title: "Follow up",
                    submissionStageKey: "second",
                    requirements: [conditional]
                },
            ],
            groups: [{groupKey: "entries", parentGroupKey: "entries", minOccurrences: 2, maxOccurrences: 1}],
            conditionRules: [{
                ruleKey: "when-note",
                predicates: [{operator: FieldOperator.EQUALS, sourceRequirementKey: "missing-note"}],
            }],
            reviewStages: [{
                stageKey: "first-review",
                title: "First review",
                aggregation: InformationRequestReviewAggregation.QUORUM,
                minimumReviewerCount: 1,
                tieResolution: InformationRequestReviewTieResolution.REQUIRE_OVERRIDE,
                sectionKeys: ["collected-data"],
            }],
        }));

        expect(problems.map(problem => problem.message)).toEqual([
            "Section \"Collected data\" needs a submission stage because this Template is submitted in stages.",
            "Group \"entries\" cannot be nested in itself.",
            "Group \"entries\" allows fewer occurrences than it requires.",
            "Group \"entries\" holds no requirement, so make a requirement repeat per it or per a group nested in it.",
            "Condition \"when-note\" reads \"missing-note\", which is not a requirement of this Template.",
            "Review stage \"First review\" decides by quorum, so state how many reviewers make one.",
            "Review stage \"First review\" resolves a tie by override, so it permits an override.",
            "Requirement \"Provide other-note\" is reviewed, but no review stage covers section \"Follow up\".",
        ]);
        expect(problems.map(problem => problem.target.panel)).toEqual([
            "sections", "groups", "groups", "groups", "conditions", "review", "review", "review",
        ]);
    });

    it("keeps the alternative that completes a staged document in its own stage", () => {
        const primary = {
            ...newRequirement(InformationRequestRequirementType.DOCUMENT, "period-records", "Attach the period records"),
            substituteRequirementKeys: ["alternate-record"],
        };
        const confirmation = {
            ...newRequirement(InformationRequestRequirementType.RESPONSE_ATTESTATION, "recorded-assertion", "Confirm the records"),
            supportingEvidenceRequirementKeys: ["alternate-record"],
        };
        const alternate = {
            ...newRequirement(InformationRequestRequirementType.DOCUMENT, "alternate-record", "Attach an alternate record"),
            requiredness: InformationRequestRequiredness.OPTIONAL,
        };
        const sections = (alternateStage: string) => [
            {
                sectionKey: "records",
                title: "Records",
                submissionStageKey: "first",
                requirements: [primary, confirmation]
            },
            {
                sectionKey: "alternates",
                title: "Alternates",
                submissionStageKey: alternateStage,
                requirements: [alternate]
            },
        ];

        const across = templateDraftProblems(documentWith({
            submissionMode: InformationRequestSubmissionMode.STAGED,
            sections: sections("later")
        }));

        expect(across.map(problem => problem.message)).toEqual([
            "Requirement \"Attach the period records\" is submitted in stage \"first\", so its alternative \"alternate-record\" from stage \"later\" cannot complete it.",
        ]);
        expect(across.map(problem => problem.target)).toEqual([{
            panel: "sections",
            sectionIndex: 0,
            requirementIndex: 0
        }]);
        expect(messages(documentWith({
            submissionMode: InformationRequestSubmissionMode.STAGED,
            sections: sections(" first ")
        }))).toEqual([]);
        expect(messages(documentWith({
            sections: sections("").map(section => ({
                ...section,
                submissionStageKey: undefined
            }))
        }))).toEqual([]);
    });

    it("refuses group occurrence bounds the server refuses", () => {
        expect(messages(groupedBy({groupKey: "entries", minOccurrences: 0, maxOccurrences: 0})))
            .toEqual(["Group \"entries\" must allow at least one occurrence."]);
        expect(messages(groupedBy({groupKey: "entries", minOccurrences: -1})))
            .toEqual(["Group \"entries\" cannot require a negative number of occurrences."]);
        expect(messages(groupedBy({groupKey: "entries", minOccurrences: 3, maxOccurrences: 2})))
            .toEqual(["Group \"entries\" allows fewer occurrences than it requires."]);
        expect(messages(groupedBy({groupKey: "entries", minOccurrences: 2, maxOccurrences: 2}))).toEqual([]);
        expect(messages(groupedBy({groupKey: "entries", minOccurrences: 0, maxOccurrences: 1}))).toEqual([]);
        expect(messages(groupedBy({groupKey: "entries"}))).toEqual([]);
    });

    it("needs every group to hold a requirement somewhere beneath it", () => {
        const groups = [{groupKey: "entries"}, {groupKey: "entry-details", parentGroupKey: "entries"}];

        expect(messages(documentWith({groups}))).toEqual([
            "Group \"entries\" holds no requirement, so make a requirement repeat per it or per a group nested in it.",
            "Group \"entry-details\" holds no requirement, so make a requirement repeat per it or per a group nested in it.",
        ]);
        expect(messages(documentWith({
            sections: [{
                sectionKey: "collected-data",
                title: "Collected data",
                requirements: [{...typed("recorded-note"), occurrenceAnchorKey: "entry-details"}],
            }],
            groups,
        }))).toEqual([]);
    });

    it("keeps a condition only on a requirement asked when it is true", () => {
        expect(messages(documentWith({
            sections: [{
                sectionKey: "collected-data",
                title: "Collected data",
                requirements: [
                    typed("recorded-note"),
                    {
                        ...typed("other-note"),
                        requiredness: InformationRequestRequiredness.REQUIRED,
                        conditionalRuleKey: "when-note",
                    },
                ],
            }],
            conditionRules: [{
                ruleKey: "when-note",
                predicates: [{operator: FieldOperator.IS_NOT_EMPTY, sourceRequirementKey: "recorded-note"}],
            }],
        }))).toEqual([
            "Requirement \"Provide other-note\" is not asked only when a condition is true, so remove its condition \"when-note\".",
        ]);
    });

    it.each([
        InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
        InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE,
    ])("keeps another contributor role's hidden answer instead of letting %s remove it", policy => {
        const document = (hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy) => documentWith({
            sections: [{
                sectionKey: "collected-data",
                title: "Collected data",
                requirements: [
                    typed("recorded-note"),
                    {
                        ...typed("other-note"),
                        contributorRole: InformationRequestContributorRole.PREPARER,
                        requiredness: InformationRequestRequiredness.CONDITIONAL,
                        conditionalRuleKey: "when-note",
                    },
                ],
            }],
            conditionRules: [{
                ruleKey: "when-note",
                hiddenDataPolicy,
                predicates: [{
                    operator: FieldOperator.IS_NOT_EMPTY,
                    fieldDefinitionId: "field-recorded-note",
                    valueType: FieldValueType.SHORT_TEXT
                }],
            }],
        });

        const problems = templateDraftProblems(document(policy));

        expect(problems.map(problem => problem.message)).toEqual([
            "Condition \"when-note\" would remove the hidden answer to \"Provide other-note\", which a different role gives than its source \"Provide recorded-note\", so it has to keep hidden answers securely.",
        ]);
        expect(problems.map(problem => problem.target)).toEqual([{panel: "conditions", ruleIndex: 0}]);
        expect(messages(document(InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY))).toEqual([]);
    });

    it("reads a staged condition only from the stage of the requirement it governs", () => {
        const governed = {
            ...newRequirement(InformationRequestRequirementType.DOCUMENT, "follow-on-record", "Attach the follow-on record"),
            requiredness: InformationRequestRequiredness.CONDITIONAL,
            conditionalRuleKey: "when-record-given",
        };
        const source = newRequirement(InformationRequestRequirementType.DOCUMENT, "first-record", "Attach the first record");
        const document = (governedStage: string) => documentWith({
            submissionMode: InformationRequestSubmissionMode.STAGED,
            sections: [
                {sectionKey: "records", title: "Records", submissionStageKey: "first", requirements: [source]},
                {
                    sectionKey: "follow-ons",
                    title: "Follow-ons",
                    submissionStageKey: governedStage,
                    requirements: [governed]
                },
            ],
            conditionRules: [{
                ruleKey: "when-record-given",
                predicates: [{
                    operator: FieldOperator.EQUALS,
                    sourceRequirementKey: "first-record",
                    expectedDisposition: InformationRequestResponseDisposition.PROVIDED,
                }],
            }],
        });

        const problems = templateDraftProblems(document("later"));

        expect(problems.map(problem => problem.message)).toEqual([
            "Requirement \"Attach the follow-on record\" is submitted in stage \"later\", so its condition \"when-record-given\" cannot read \"first-record\" from stage \"first\".",
        ]);
        expect(problems.map(problem => problem.target)).toEqual([{
            panel: "sections",
            sectionIndex: 1,
            requirementIndex: 0
        }]);
        expect(messages(document("first"))).toEqual([]);
    });

    it("reads a disposition condition only with a disposition to compare", () =>
    {
        expect(messages(documentWith({
            conditionRules: [{
                ruleKey: "when-note",
                predicates: [
                    {operator: FieldOperator.EQUALS, sourceRequirementKey: "recorded-note"},
                    {
                        operator: FieldOperator.EQUALS,
                        sourceRequirementKey: "recorded-note",
                        expectedDisposition: InformationRequestResponseDisposition.NOT_APPLICABLE,
                    },
                ],
            }],
        }))).toEqual(["Condition \"when-note\" compares an answer but states no answer to compare with."]);
    });

    it("turns a server refusal into the place in the editor it is about", () =>
    {
        const document = documentWith({
            groups: [{groupKey: "entries"}],
            conditionRules: [{ruleKey: "when-note", predicates: []}],
            reviewStages: [{stageKey: "first-review", title: "First review"}],
        });

        expect(targetForRefusal(document, {
            errorMessage: "refused",
            reasonCode: "INFORMATION_REQUEST_TEMPLATE_INVALID",
            sectionKey: "collected-data",
            requirementKey: "recorded-note",
        })).toEqual({panel: "sections", sectionIndex: 0, requirementIndex: 0});
        expect(targetForRefusal(document, {errorMessage: "refused", reasonCode: "x", groupKey: "entries"}))
            .toEqual({panel: "groups", groupIndex: 0});
        expect(targetForRefusal(document, {errorMessage: "refused", reasonCode: "x", conditionRuleKey: "when-note"}))
            .toEqual({panel: "conditions", ruleIndex: 0});
        expect(targetForRefusal(document, {errorMessage: "refused", reasonCode: "x", reviewStageKey: "first-review"}))
            .toEqual({panel: "review", stageIndex: 0});
        expect(targetForRefusal(document, {errorMessage: "refused", reasonCode: "x"})).toBeUndefined();
    });

    it("keeps a condition refusal on the condition when a group shares its key", () => {
        const document = documentWith({
            groups: [{groupKey: "shared-key"}],
            conditionRules: [{ruleKey: "shared-key", predicates: []}],
        });

        expect(targetForRefusal(document, {errorMessage: "refused", reasonCode: "x", conditionRuleKey: "shared-key"}))
            .toEqual({panel: "conditions", ruleIndex: 0});
        expect(targetForRefusal(document, {errorMessage: "refused", reasonCode: "x", groupKey: "shared-key"}))
            .toEqual({panel: "groups", groupIndex: 0});
    });
});
