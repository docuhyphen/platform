/** @vitest-environment jsdom */
import {cleanup, render, screen} from "@testing-library/react";
import {afterAll, afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import apiClient, {setApiClientAuthToken} from "../apiClient.ts";
import {listSchemas} from "../fieldsService.ts";
import {getInformationRequestTemplate} from "../informationRequestTemplateService.ts";
import {jsonAnswer, LocalAnswer, LocalApiServer, startLocalApiServer} from "../testing/localApiServer.ts";
import {
    InformationRequestClockDto,
    InformationRequestClockState,
    InformationRequestDisposalStandingDto,
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestEvidenceConformancePolicy,
    InformationRequestEvidenceWaiverPolicy,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestTemplateDto,
    InformationRequestTemplateGroupDto,
    InformationRequestTemplateRequirementDto,
    RecordRetentionScheduleDto,
    RecordRetentionScheduleVersionDto,
    SchemaDefinitionDto,
} from "../../app/models/models.tsx";
import {
    requestSchema,
    template
} from "../../app/settings/workflows-tab/workflow-designer/requirement-condition-editor/requirementConditionTestFixtures.ts";
import {
    requirementChoicesOf
} from "../../app/settings/workflows-tab/workflow-designer/requirement-condition-editor/requirementConditionChoices.ts";
import {draftDocumentFromVersion} from "../../app/information-requests/template-document/templateDraftDocument.ts";
import {templateDraftProblems} from "../../app/information-requests/template-document/templateDraftValidation.ts";
import ClockHistoryPanel from "../../app/information-requests/operations/clock-history/ClockHistoryPanel.tsx";
import RecordStandingPanel from "../../app/information-requests/operations/record-standing/RecordStandingPanel.tsx";
import RetentionScheduleCard from "../../app/record-preservation/retention-schedule/RetentionScheduleCard.tsx";

vi.mock("../../context/AuthContext.tsx", () => ({
    useAuth: () => ({currentSession: {activeOrganizationId: undefined as string | undefined}, hasCapability: () => true}),
}));

const published = template.latestPublishedVersion;
if (!published) throw new Error("The fixture Template has a published Version");

const sentTemplate: InformationRequestTemplateDto = {
    ...template,
    draftVersion: null,
    latestPublishedVersion: {
        ...published,
        groups: published.groups.map((group): InformationRequestTemplateGroupDto => ({
            ...group,
            parentGroupKey: group.parentGroupKey ?? null,
            maxOccurrences: null
        })),
        sections: published.sections.map(section => ({
            ...section,
            requirements: section.requirements.map((requirement): InformationRequestTemplateRequirementDto => requirement.requirementType === InformationRequestRequirementType.DOCUMENT
                ? {
                    ...requirement,
                    responseMode: InformationRequestResponseMode.PROVIDE,
                    requiredness: InformationRequestRequiredness.REQUIRED,
                    permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
                    substituteRequirementKeys: [],
                    supportingEvidenceRequirementKeys: [],
                    evidencePolicy: {
                        id: "policy-a",
                        minimumFileCount: 1,
                        maximumFileCount: null,
                        maximumFileSizeBytes: null,
                        maximumTotalSizeBytes: null,
                        minimumPageCount: null,
                        maximumPageCount: null,
                        issuerRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        jurisdictionRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        languageRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        issueDateRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        expiryDateRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        coveragePeriodRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        certificationRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        signatureRequirement: InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
                        maximumIssueAgeDays: null,
                        minimumRemainingValidityDays: null,
                        minimumCoverageDays: null,
                        coverageContinuityRequired: false,
                        waiverPolicy: InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED,
                        conformancePolicy: InformationRequestEvidenceConformancePolicy.CONFORMANCE_REQUIRED,
                        acceptedValues: [],
                    },
                }
                : {
                    ...requirement,
                    substituteRequirementKeys: [],
                    supportingEvidenceRequirementKeys: [],
                    evidencePolicy: null
                }),
        })),
    },
};

const schemaVersion = (requestSchema as unknown as { latestPublishedVersion: object }).latestPublishedVersion;

const sentSchemas = [
    {
        ...requestSchema,
        id: "schema-b",
        latestPublishedVersion: null,
        draftVersion: {...schemaVersion, id: "schema-version-b"}
    },
    {...requestSchema, draftVersion: null},
] as unknown as SchemaDefinitionDto[];

const clock = (id: string, state: InformationRequestClockState, remainingSeconds: number | null): InformationRequestClockDto => ({
    id,
    clockKey: id,
    policyVersionId: "policy-version-a",
    policyVersionNumber: 1,
    clockType: "CALENDAR",
    urgency: "STANDARD",
    receivedAt: Date.parse("2026-10-01T08:00:00Z"),
    state,
    dueAt: Date.parse("2026-10-02T08:00:00Z"),
    dueCycle: 1,
    remainingSeconds,
    overdueAt: null,
    stoppedAt: null,
    clockETag: `"${id}-1"`,
    events: [],
});

const retentionVersion = (versionNumber: number, disposalAfterDays: number | null): RecordRetentionScheduleVersionDto => ({
    id: `retention-${versionNumber}`,
    versionNumber,
    minimumRetentionDays: 365,
    disposalAfterDays,
    recordedByPrincipalKind: "USER",
    recordedByPrincipalId: "user-a",
    recordedAt: Date.parse("2026-09-01T08:00:00Z"),
});

const retention: RecordRetentionScheduleDto = {
    resourceType: "INFORMATION_REQUEST",
    current: retentionVersion(2, null),
    versions: [retentionVersion(1, 30), retentionVersion(2, null)],
    scheduleETag: "\"retention-2\"",
};

const standing: InformationRequestDisposalStandingDto = {
    requestId: "request-a",
    eligible: false,
    reasonCode: null,
    detail: null,
    eligibleFrom: null,
    schedule: retentionVersion(2, null),
    holds: [],
    withheldHolds: [],
    retainedObjectCount: 0,
    disposal: null,
};

const notFound = jsonAnswer(404, {reasonCode: "INFORMATION_REQUEST_NOT_FOUND", errorMessage: "Not found"});

const RETENTION_READ = "GET /record-retention-schedules/INFORMATION_REQUEST";

const answers: Record<string, LocalAnswer> = {
    "GET /information-request-templates/template-a": jsonAnswer(200, sentTemplate),
    "GET /schemas/definitions": jsonAnswer(200, sentSchemas),
    "GET /information-requests/request-a/clocks": jsonAnswer(200, [
        clock("clock-running", InformationRequestClockState.RUNNING, null),
        clock("clock-paused", InformationRequestClockState.PAUSED, 5400),
    ]),
    "GET /information-requests/request-a/disposal-standing": jsonAnswer(200, standing),
    [RETENTION_READ]: jsonAnswer(200, retention),
};

let server: LocalApiServer;

describe("Information Request responses whose unset values the server sends as null", () => {
    beforeAll(async () => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
        server = await startLocalApiServer((method, path) => answers[`${method} ${path.split("?")[0]}`] ?? notFound);
        apiClient.defaults.adapter = "xhr";
        apiClient.defaults.baseURL = server.origin;
        setApiClientAuthToken(null);
    });

    afterEach(() => cleanup());

    afterAll(async () => {
        vi.unstubAllGlobals();
        await server.close();
    });

    it("sends the unset values as explicit nulls", () => {
        expect(answers["GET /information-request-templates/template-a"].body).toContain(`"parentGroupKey":null`);
        expect(answers["GET /information-request-templates/template-a"].body).toContain(`"maximumFileCount":null`);
        expect(answers["GET /schemas/definitions"].body).toContain(`"draftVersion":null`);
        expect(answers["GET /information-requests/request-a/clocks"].body).toContain(`"remainingSeconds":null`);
        expect(answers[RETENTION_READ].body).toContain(`"disposalAfterDays":null`);
    });

    it("offers condition answers at their first occurrence even when a schema has no draft or no published version", async () => {
        const [read, schemas] = await Promise.all([getInformationRequestTemplate("template-a"), listSchemas()]);

        const choices = requirementChoicesOf(read.latestPublishedVersion, schemas);

        expect(choices.map(choice => `${choice.requirementKey}@${choice.occurrencePath}`))
            .toEqual(["amount@root", "category@root", "line-total@lines[0]/items[0]"]);
    });

    it("starts a draft from a Version without file limits that reports no file limit problem", async () => {
        const read = await getInformationRequestTemplate("template-a");

        const messages = templateDraftProblems(draftDocumentFromVersion(read.latestPublishedVersion ?? undefined)).map(problem => problem.message);

        expect(messages.join(" ")).not.toMatch(/files allowed|positive file size limit/);
    });

    it("shows time left only for a paused clock", async () => {
        render(<ClockHistoryPanel requestId={"request-a"}/>);

        await screen.findByText(/Time left when paused: 1h 30m/);
        expect(document.getElementById("information-request-clock-clock-running-remaining")).toBeNull();
        expect(screen.getAllByText(/Time left when paused/)).toHaveLength(1);
    });

    it("describes a retention schedule without automatic disposal as never disposing", async () => {
        render(<RetentionScheduleCard canManage={false}/>);

        await screen.findByText(/Version 2: kept at least 365 days after finishing, never disposed automatically\./);
        expect(document.body.textContent).not.toContain("null");
        cleanup();

        const sent = answers[RETENTION_READ];
        answers[RETENTION_READ] = jsonAnswer(200, {...retention, current: retentionVersion(1, 30)});
        try {
            render(<RetentionScheduleCard canManage={false}/>);
            expect(await screen.findByText(/Version 1: kept at least 365 days, disposed 30 days after finishing\./)).toBeTruthy();
        } finally {
            answers[RETENTION_READ] = sent;
        }
    });

    it("describes a request's retention schedule without automatic disposal as never disposing", async () => {
        render(<RecordStandingPanel requestId={"request-a"}/>);

        await screen.findByText(/never disposed automatically/);
        expect(document.body.textContent).not.toContain("null");
    });
});
