import {describe, expect, it} from "vitest";
import {
    FieldOperator,
    FieldValueType,
    InformationRequestAttestationOrdering,
    InformationRequestAuthenticationStrength,
    InformationRequestConditionHiddenDataPolicy,
    InformationRequestContributorRole,
    InformationRequestEvidenceAttribute,
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestEvidenceConformancePolicy,
    InformationRequestEvidenceWaiverPolicy,
    InformationRequestExternalSignatureReferencePolicy,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestReviewAggregation,
    InformationRequestReviewPolicy,
    InformationRequestReviewStageOrdering,
    InformationRequestReviewTieResolution,
    InformationRequestSubmissionMode,
    InformationRequestSubmissionStageOrdering,
    InformationRequestTemplateStatus,
    InformationRequestTemplateVersionDto,
} from "../../models/models.tsx";
import {
    configurationRequestFrom,
    draftDocumentFromVersion,
    emptyDraftDocument,
    keyFromLabel,
    moveItem,
    newRequirement,
    removeItem,
    replaceItem,
} from "./templateDraftDocument.ts";

const publishedVersion = (): InformationRequestTemplateVersionDto => ({
    id: "version-2",
    templateDefinitionId: "template-1",
    versionNumber: 2,
    status: InformationRequestTemplateStatus.PUBLISHED,
    schemaVersionId: "schema-version-1",
    submissionMode: InformationRequestSubmissionMode.STAGED,
    submissionStageOrdering: InformationRequestSubmissionStageOrdering.SEQUENTIAL,
    reviewStageOrdering: InformationRequestReviewStageOrdering.PARALLEL,
    factReusePurposeKey: "repeat-collection",
    reviewStages: [{
        id: "stage-row-1",
        stageKey: "first-review",
        title: "First review",
        aggregation: InformationRequestReviewAggregation.QUORUM,
        quorumCount: 2,
        minimumReviewerCount: 3,
        tieResolution: InformationRequestReviewTieResolution.REQUIRE_OVERRIDE,
        overridePermitted: true,
        excludesResponseParties: true,
        excludesPriorReviewers: false,
        sectionKeys: ["collected-data"],
    }],
    groups: [{id: "group-row-1", groupKey: "entries", minOccurrences: 1, maxOccurrences: 4}],
    conditionRules: [{
        id: "rule-row-1",
        ruleKey: "when-note-applies",
        expressionVersion: 1,
        hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
        predicates: [{
            id: "predicate-row-1",
            fieldDefinitionId: "field-1",
            valueType: FieldValueType.BOOLEAN,
            operator: FieldOperator.EQUALS,
            value: true,
        }],
    }],
    sections: [
        {
            id: "section-row-1",
            sectionKey: "collected-data",
            title: "Collected data",
            helpText: "Answer each question",
            submissionStageKey: "first",
            requirements: [{
                id: "binding-1",
                templateRequirementId: "requirement-1",
                requirementKey: "recorded-note",
                requirementType: InformationRequestRequirementType.FIELD,
                prompt: "State the recorded note",
                responseMode: InformationRequestResponseMode.PROVIDE,
                requiredness: InformationRequestRequiredness.CONDITIONAL,
                contributorRole: InformationRequestContributorRole.PREPARER,
                reviewPolicy: InformationRequestReviewPolicy.REQUIRED,
                conditionalRuleKey: "when-note-applies",
                occurrenceAnchorKey: "entries",
                collectedFieldDefinitionId: "field-2",
                permittedDispositions: [
                    InformationRequestResponseDisposition.PROVIDED,
                    InformationRequestResponseDisposition.NOT_APPLICABLE,
                ],
                substituteRequirementKeys: [],
                supportingEvidenceRequirementKeys: ["supporting-file"],
            }],
        },
        {
            id: "section-row-2",
            sectionKey: "files",
            title: "Files",
            submissionStageKey: "second",
            requirements: [
                {
                    id: "binding-2",
                    templateRequirementId: "requirement-2",
                    requirementKey: "supporting-file",
                    requirementType: InformationRequestRequirementType.DOCUMENT,
                    prompt: "Attach the supporting file",
                    responseMode: InformationRequestResponseMode.PROVIDE,
                    requiredness: InformationRequestRequiredness.REQUIRED,
                    contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
                    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
                    permittedDispositions: [
                        InformationRequestResponseDisposition.PROVIDED,
                        InformationRequestResponseDisposition.WAIVED,
                    ],
                    evidencePolicy: {
                        id: "policy-row-1",
                        minimumFileCount: 1,
                        maximumFileCount: 3,
                        maximumFileSizeBytes: 1048576,
                        issuerRequirement: InformationRequestEvidenceAttributeRequirement.REQUIRED,
                        jurisdictionRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        languageRequirement: InformationRequestEvidenceAttributeRequirement.OPTIONAL,
                        issueDateRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        expiryDateRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        coveragePeriodRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        certificationRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        signatureRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        maximumIssueAgeDays: 90,
                        coverageContinuityRequired: false,
                        waiverPolicy: InformationRequestEvidenceWaiverPolicy.RESPONDENT_DECLARED,
                        conformancePolicy: InformationRequestEvidenceConformancePolicy.CONFORMANCE_REQUIRED,
                        acceptedValues: [{
                            attribute: InformationRequestEvidenceAttribute.ISSUER,
                            acceptedValue: "Issuing office",
                        }],
                    },
                    substituteRequirementKeys: [],
                    supportingEvidenceRequirementKeys: [],
                },
                {
                    id: "binding-3",
                    templateRequirementId: "requirement-3",
                    requirementKey: "confirmation",
                    requirementType: InformationRequestRequirementType.RESPONSE_ATTESTATION,
                    prompt: "Confirm the answers are complete",
                    responseMode: InformationRequestResponseMode.PROVIDE,
                    requiredness: InformationRequestRequiredness.REQUIRED,
                    contributorRole: InformationRequestContributorRole.ATTESTOR,
                    reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
                    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
                    substituteRequirementKeys: [],
                    supportingEvidenceRequirementKeys: [],
                    attestationPolicy: {
                        id: "attestation-row-1",
                        requiredRoles: [InformationRequestContributorRole.ATTESTOR],
                        ordering: InformationRequestAttestationOrdering.ANY_ORDER,
                        minimumAssentCount: 1,
                        minimumAuthenticationStrength: InformationRequestAuthenticationStrength.ACCOUNT_SIGN_IN,
                        validityHours: 48,
                        externalSignatureReference: InformationRequestExternalSignatureReferencePolicy.OPTIONAL,
                    },
                },
            ],
        },
    ],
    requiredCapabilities: [],
    createdAt: "2026-09-27T00:00:00Z",
});

describe("templateDraftDocument", () =>
{
    it("reads every authored statement of a Version back into the configuration it was written from", () =>
    {
        const request = configurationRequestFrom(draftDocumentFromVersion(publishedVersion()));

        expect(request.schemaVersionId).toBe("schema-version-1");
        expect(request.submissionMode).toBe(InformationRequestSubmissionMode.STAGED);
        expect(request.submissionStageOrdering).toBe(InformationRequestSubmissionStageOrdering.SEQUENTIAL);
        expect(request.reviewStageOrdering).toBe(InformationRequestReviewStageOrdering.PARALLEL);
        expect(request.factReusePurposeKey).toBe("repeat-collection");
        expect(request.reviewStages).toEqual([{
            stageKey: "first-review",
            title: "First review",
            aggregation: InformationRequestReviewAggregation.QUORUM,
            quorumCount: 2,
            minimumReviewerCount: 3,
            tieResolution: InformationRequestReviewTieResolution.REQUIRE_OVERRIDE,
            overridePermitted: true,
            excludesResponseParties: true,
            excludesPriorReviewers: false,
            sectionKeys: ["collected-data"],
        }]);
        expect(request.groups).toEqual([{groupKey: "entries", minOccurrences: 1, maxOccurrences: 4}]);
        expect(request.conditionRules).toEqual([{
            ruleKey: "when-note-applies",
            expressionVersion: 1,
            hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
            predicates: [{
                fieldDefinitionId: "field-1",
                valueType: FieldValueType.BOOLEAN,
                operator: FieldOperator.EQUALS,
                value: true,
            }],
        }]);
        expect(request.sections.map(section => section.sectionKey)).toEqual(["collected-data", "files"]);
        expect(request.sections[0]).toMatchObject({
            title: "Collected data",
            helpText: "Answer each question",
            submissionStageKey: "first",
        });
        expect(request.sections[0].requirements[0]).toEqual({
            requirementKey: "recorded-note",
            requirementType: InformationRequestRequirementType.FIELD,
            prompt: "State the recorded note",
            responseMode: InformationRequestResponseMode.PROVIDE,
            requiredness: InformationRequestRequiredness.CONDITIONAL,
            contributorRole: InformationRequestContributorRole.PREPARER,
            reviewPolicy: InformationRequestReviewPolicy.REQUIRED,
            conditionalRuleKey: "when-note-applies",
            occurrenceAnchorKey: "entries",
            collectedFieldDefinitionId: "field-2",
            permittedDispositions: [
                InformationRequestResponseDisposition.PROVIDED,
                InformationRequestResponseDisposition.NOT_APPLICABLE,
            ],
            substituteRequirementKeys: [],
            supportingEvidenceRequirementKeys: ["supporting-file"],
        });
        const [document, attestation] = request.sections[1].requirements;
        expect(document.evidencePolicy).toMatchObject({
            minimumFileCount: 1,
            maximumFileCount: 3,
            maximumFileSizeBytes: 1048576,
            issuerRequirement: InformationRequestEvidenceAttributeRequirement.REQUIRED,
            languageRequirement: InformationRequestEvidenceAttributeRequirement.OPTIONAL,
            maximumIssueAgeDays: 90,
            waiverPolicy: InformationRequestEvidenceWaiverPolicy.RESPONDENT_DECLARED,
            acceptedValues: [{attribute: InformationRequestEvidenceAttribute.ISSUER, acceptedValue: "Issuing office"}],
        });
        expect(document.evidencePolicy).not.toHaveProperty("id");
        expect(attestation.attestationPolicy).toEqual({
            requiredRoles: [InformationRequestContributorRole.ATTESTOR],
            ordering: InformationRequestAttestationOrdering.ANY_ORDER,
            minimumAssentCount: 1,
            minimumAuthenticationStrength: InformationRequestAuthenticationStrength.ACCOUNT_SIGN_IN,
            validityHours: 48,
            externalSignatureReference: InformationRequestExternalSignatureReferencePolicy.OPTIONAL,
        });
    });

    it("drops submission stage keys when the Version submits everything at once", () =>
    {
        const document = draftDocumentFromVersion(publishedVersion());

        const request = configurationRequestFrom({
            ...document,
            submissionMode: InformationRequestSubmissionMode.WHOLE_PACKAGE,
            submissionStageOrdering: InformationRequestSubmissionStageOrdering.SEQUENTIAL,
        });

        expect(request.sections.map(section => section.submissionStageKey)).toEqual([undefined, undefined]);
        expect(request.submissionStageOrdering).toBe(InformationRequestSubmissionStageOrdering.ANY_ORDER);
    });

    it("starts an empty document that submits once and is reviewed by nobody", () =>
    {
        expect(emptyDraftDocument()).toEqual({
            sections: [],
            groups: [],
            conditionRules: [],
            submissionMode: InformationRequestSubmissionMode.WHOLE_PACKAGE,
            submissionStageOrdering: InformationRequestSubmissionStageOrdering.ANY_ORDER,
            reviewStageOrdering: InformationRequestReviewStageOrdering.SEQUENTIAL,
            reviewStages: [],
        });
        expect(draftDocumentFromVersion(undefined)).toEqual(emptyDraftDocument());
    });

    it("gives a new Document Requirement a policy and a new confirmation its assenting role", () =>
    {
        const document = newRequirement(InformationRequestRequirementType.DOCUMENT, "supporting-file", "Attach it");
        const confirmation = newRequirement(InformationRequestRequirementType.RESPONSE_ATTESTATION, "confirm", "Confirm");
        const typed = newRequirement(InformationRequestRequirementType.FIELD, "note", "State it");

        expect(document.evidencePolicy?.minimumFileCount).toBe(1);
        expect(document.evidencePolicy?.waiverPolicy).toBe(InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED);
        expect(document.permittedDispositions).toEqual([InformationRequestResponseDisposition.PROVIDED]);
        expect(confirmation.attestationPolicy?.requiredRoles).toEqual([InformationRequestContributorRole.CONTRIBUTOR]);
        expect(typed.evidencePolicy).toBeUndefined();
        expect(typed.attestationPolicy).toBeUndefined();
        expect(typed.requiredness).toBe(InformationRequestRequiredness.REQUIRED);
    });

    it("moves, replaces, and removes list items without changing the original list", () =>
    {
        const items = ["first", "second", "third"];

        expect(moveItem(items, 0, 1)).toEqual(["second", "first", "third"]);
        expect(moveItem(items, 2, -1)).toEqual(["first", "third", "second"]);
        expect(moveItem(items, 0, -1)).toEqual(items);
        expect(moveItem(items, 2, 1)).toEqual(items);
        expect(replaceItem(items, 1, "other")).toEqual(["first", "other", "third"]);
        expect(removeItem(items, 0)).toEqual(["second", "third"]);
        expect(items).toEqual(["first", "second", "third"]);
    });

    it("derives a machine key from a label", () =>
    {
        expect(keyFromLabel("  Supporting File (latest) ")).toBe("supporting-file-latest");
        expect(keyFromLabel("---")).toBe("");
        expect(keyFromLabel("Step 2: Review")).toBe("step-2-review");
    });
});
