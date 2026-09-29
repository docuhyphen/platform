import {describe, expect, it} from "vitest";
import {
    FieldOperator,
    InformationRequestEvidenceWaiverPolicy,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestReviewAggregation,
    InformationRequestReviewPolicy,
    InformationRequestReviewTieResolution,
    InformationRequestSubmissionMode,
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

describe("templateDraftValidation", () =>
{
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
                {sectionKey: "follow-up", title: "Follow up", submissionStageKey: "second", requirements: [conditional]},
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
            "Condition \"when-note\" reads \"missing-note\", which is not a requirement of this Template.",
            "Review stage \"First review\" decides by quorum, so state how many reviewers make one.",
            "Review stage \"First review\" resolves a tie by override, so it permits an override.",
            "Requirement \"Provide other-note\" is reviewed, but no review stage covers section \"Follow up\".",
        ]);
        expect(problems.map(problem => problem.target.panel)).toEqual([
            "sections", "groups", "groups", "conditions", "review", "review", "review",
        ]);
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
        expect(targetForRefusal(document, {errorMessage: "refused", reasonCode: "x", groupKey: "when-note"}))
            .toEqual({panel: "conditions", ruleIndex: 0});
        expect(targetForRefusal(document, {errorMessage: "refused", reasonCode: "x", reviewStageKey: "first-review"}))
            .toEqual({panel: "review", stageIndex: 0});
        expect(targetForRefusal(document, {errorMessage: "refused", reasonCode: "x"})).toEqual({panel: "settings"});
    });
});
