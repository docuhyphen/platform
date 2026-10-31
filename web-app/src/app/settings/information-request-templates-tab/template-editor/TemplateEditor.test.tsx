/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    FieldDataClassification,
    FieldLifecycleStatus,
    FieldScopeKind,
    FieldValueType,
    InformationRequestContributorRole,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestReviewPolicy,
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    InformationRequestTemplateVersionDto,
    SchemaDefinitionDto,
} from "../../../models/models.tsx";
import {getSchemaVersion} from "../../../../services/fieldsService.ts";
import {getInformationRequestTemplateVersion} from "../../../../services/informationRequestTemplateService.ts";
import {formatInformationRequestTime} from "../../../information-requests/shared/informationRequestFormatting.ts";
import TemplateEditor from "./TemplateEditor.tsx";
import {TemplateEditorCommands} from "./templateEditorTypes.ts";

vi.mock("../../../../services/fieldsService.ts", () => ({getSchemaVersion: vi.fn()}));
vi.mock("../../../../services/informationRequestTemplateService.ts", () => ({getInformationRequestTemplateVersion: vi.fn()}));

beforeAll(() =>
{
    globalThis.ResizeObserver = class
    {
        observe() {
        }

        unobserve() {
        }

        disconnect() {
        }
    };
});

afterEach(cleanup);

const schema = (): SchemaDefinitionDto => ({
    id: "schema-1",
    scopeKind: FieldScopeKind.ORGANIZATION,
    namespace: "process",
    schemaKey: "request-schema",
    displayName: "Request Schema",
    targetResourceType: "INFORMATION_REQUEST",
    status: FieldLifecycleStatus.PUBLISHED,
    latestPublishedVersion: {
        id: "schema-version-1",
        schemaDefinitionId: "schema-1",
        versionNumber: 1,
        status: FieldLifecycleStatus.PUBLISHED,
        bindings: [{
            id: "binding-1",
            fieldContractId: "field-contract-1",
            fieldDefinitionId: "field-1",
            namespace: "process",
            fieldKey: "recorded-summary",
            label: "Recorded summary",
            valueType: FieldValueType.SHORT_TEXT,
            displayOrder: 0,
            isRequired: true,
            isReadOnly: false,
            visibility: FieldDataClassification.INTERNAL,
            constraints: {},
            options: [],
        }],
        createdAt: Date.parse("2026-09-02T00:00:00Z"),
    },
    createdAt: Date.parse("2026-09-02T00:00:00Z"),
});

const retiredSchema = (): SchemaDefinitionDto => ({
    id: "schema-r",
    scopeKind: FieldScopeKind.PLATFORM,
    namespace: "platform",
    schemaKey: "withdrawn-questions",
    displayName: "Withdrawn questions",
    targetResourceType: "INFORMATION_REQUEST",
    status: FieldLifecycleStatus.RETIRED,
    latestPublishedVersion: {
        id: "schema-version-r",
        schemaDefinitionId: "schema-r",
        versionNumber: 2,
        status: FieldLifecycleStatus.PUBLISHED,
        bindings: [],
        createdAt: Date.parse("2026-09-02T00:00:00Z"),
    },
    createdAt: Date.parse("2026-09-02T00:00:00Z"),
});

const template = (withDraft = true): InformationRequestTemplateDto => ({
    id: "template-1",
    scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION,
    namespace: "process",
    templateKey: "collection-pattern",
    displayName: "Collection pattern",
    status: withDraft ? InformationRequestTemplateStatus.DRAFT : InformationRequestTemplateStatus.PUBLISHED,
    draftVersion: withDraft ? {
        id: "draft-version-1",
        templateDefinitionId: "template-1",
        versionNumber: 1,
        status: InformationRequestTemplateStatus.DRAFT,
        sections: [],
        groups: [],
        conditionRules: [],
        requiredCapabilities: [],
        createdAt: Date.parse("2026-09-02T00:00:00Z"),
    } : undefined,
    latestPublishedVersion: withDraft ? undefined : {
        id: "published-version-1",
        templateDefinitionId: "template-1",
        versionNumber: 1,
        status: InformationRequestTemplateStatus.PUBLISHED,
        schemaVersionId: "schema-version-1",
        sections: [{
            id: "section-row-1",
            sectionKey: "collected-data",
            title: "Collected data",
            requirements: [{
                id: "binding-row-1",
                templateRequirementId: "requirement-1",
                requirementKey: "recorded-summary",
                requirementType: InformationRequestRequirementType.FIELD,
                prompt: "Provide the recorded summary",
                responseMode: InformationRequestResponseMode.PROVIDE,
                requiredness: InformationRequestRequiredness.REQUIRED,
                contributorRole: InformationRequestContributorRole.CONTRIBUTOR,
                reviewPolicy: InformationRequestReviewPolicy.NOT_REQUIRED,
                collectedFieldDefinitionId: "field-1",
                permittedDispositions: [InformationRequestResponseDisposition.PROVIDED],
                substituteRequirementKeys: [],
                supportingEvidenceRequirementKeys: [],
            }],
        }],
        groups: [],
        conditionRules: [],
        requiredCapabilities: [],
        publishedAt: Date.parse("2026-09-03T00:00:00Z"),
        createdAt: Date.parse("2026-09-02T00:00:00Z"),
    },
    createdAt: Date.parse("2026-09-02T00:00:00Z"),
    updatedAt: Date.parse("2026-09-02T00:00:00Z"),
});

const earlierVersion = (
    versionNumber: number,
    status: InformationRequestTemplateStatus,
    prompt: string,
): InformationRequestTemplateVersionDto => {
    const published = template(false).latestPublishedVersion;
    if (!published) throw new Error("The fixture has a published Version");
    const section = published.sections[0];
    return {
        ...published,
        id: `published-version-${versionNumber}`,
        versionNumber,
        status,
        sections: [{...section, requirements: [{...section.requirements[0], prompt}]}],
    };
};

const twoVersions = (firstStatus = InformationRequestTemplateStatus.PUBLISHED): InformationRequestTemplateDto => {
    const current = template(false);
    if (!current.latestPublishedVersion) throw new Error("The fixture has a published Version");
    current.latestPublishedVersion = {...current.latestPublishedVersion, id: "published-version-2", versionNumber: 2};
    current.versions = [
        {
            versionNumber: 1,
            status: firstStatus,
            publishedAt: Date.parse("2026-09-01T00:00:00Z"),
            retiredAt: firstStatus === InformationRequestTemplateStatus.RETIRED ? Date.parse("2026-09-05T00:00:00Z") : undefined,
        },
        {
            versionNumber: 2,
            status: InformationRequestTemplateStatus.PUBLISHED,
            publishedAt: Date.parse("2026-09-03T00:00:00Z")
        },
    ];
    return current;
};

const commands = (): TemplateEditorCommands => ({
    save: vi.fn(async () => template()),
    publish: vi.fn(async () => template()),
    startDraft: vi.fn(async () => template()),
    retire: vi.fn(async () => template(false)),
    clone: vi.fn(async () => template()),
    adoptLatestDraft: vi.fn(async () => true),
    reload: vi.fn(async () => template()),
});

const STALE_REFUSAL = {
    errorMessage: "The data changed after it was read; reload it and try again",
    reasonCode: "COMMAND_PRECONDITION_STALE",
};

const chooseSchema = () => {
    fireEvent.click(screen.getByRole("tab", {name: "Settings"}));
    change("Request Schema", "schema-1");
};

const renderEditor = (
    current: InformationRequestTemplateDto,
    editorCommands = commands(),
    canManage = true,
    copyTargets: InformationRequestTemplateScopeKind[] = canManage ? [InformationRequestTemplateScopeKind.ORGANIZATION] : [],
    schemas: SchemaDefinitionDto[] = [schema()],
) =>
{
    render(<TemplateEditor template={current}
                           schemas={schemas}
                           schemasError={null}
                           onRetrySchemas={vi.fn()}
                           canManage={canManage}
                           copyTargets={copyTargets}
                           commands={editorCommands}
                           onClose={vi.fn()}/>);
    return editorCommands;
};

const change = (label: string, value: string, container: HTMLElement = document.body) =>
    fireEvent.change(within(container).getByLabelText(label), {target: {value}});

const dialog = () => screen.getByRole("dialog");

describe("TemplateEditor", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("offers no retired Schema for typed answers", () => {
        renderEditor(template(), commands(), true, [InformationRequestTemplateScopeKind.ORGANIZATION], [schema(), retiredSchema()]);

        fireEvent.click(screen.getByRole("tab", {name: "Settings"}));

        expect(screen.getByRole("option", {name: "Request Schema (version 1)"})).toBeTruthy();
        expect(screen.queryByRole("option", {name: "Withdrawn questions (version 2)"})).toBeNull();
    });

    it("keeps showing the retired Schema a draft already uses", () => {
        const current = template();
        if (!current.draftVersion) throw new Error("The fixture has a draft");
        renderEditor(
            {...current, draftVersion: {...current.draftVersion, schemaVersionId: "schema-version-r"}},
            commands(),
            true,
            [InformationRequestTemplateScopeKind.ORGANIZATION],
            [schema(), retiredSchema()],
        );

        fireEvent.click(screen.getByRole("tab", {name: "Settings"}));

        expect((screen.getByLabelText("Request Schema") as HTMLSelectElement).value).toBe("schema-r");
        expect(screen.getByRole("option", {name: "Withdrawn questions (version 2)"})).toBeTruthy();
        expect(within(document.getElementById("template-settings-panel")!)
            .getByText("The Request Schema this draft names was retired, so saving is refused until you choose a published one.")).toBeTruthy();
        expect(getSchemaVersion).not.toHaveBeenCalled();
    });

    it("authors ordered sections with typed, document, and confirmation requirements and saves the exact document", {timeout: 20000}, async () =>
    {
        const editorCommands = renderEditor(template());

        fireEvent.click(screen.getByRole("tab", {name: "Settings"}));
        change("Request Schema", "schema-1");
        fireEvent.click(screen.getByRole("tab", {name: "Sections"}));

        fireEvent.click(screen.getByRole("button", {name: "Add section"}));
        change("Title", "Collected data", dialog());
        expect((within(dialog()).getByLabelText("Key") as HTMLInputElement).value).toBe("collected-data");
        fireEvent.click(within(dialog()).getByRole("button", {name: "Save"}));

        fireEvent.click(screen.getByRole("button", {name: "Add requirement to Collected data"}));
        change("Prompt", "Provide the recorded summary", dialog());
        change("Key", "recorded-summary", dialog());
        change("Collected Field", "field-1", dialog());
        fireEvent.click(within(dialog()).getByRole("button", {name: "Save"}));

        fireEvent.click(screen.getByRole("button", {name: "Add requirement to Collected data"}));
        change("Requirement type", InformationRequestRequirementType.DOCUMENT, dialog());
        change("Prompt", "Attach the supporting file", dialog());
        fireEvent.click(within(dialog()).getByRole("tab", {name: "Evidence"}));
        change("Maximum files", "2", dialog());
        change("Maximum file size (MiB)", "5", dialog());
        fireEvent.click(within(dialog()).getByRole("button", {name: "Save"}));

        fireEvent.click(screen.getByRole("button", {name: "Add requirement to Collected data"}));
        change("Requirement type", InformationRequestRequirementType.RESPONSE_ATTESTATION, dialog());
        change("Prompt", "Confirm the answers are complete", dialog());
        change("Key", "completeness-confirmation", dialog());
        fireEvent.click(within(dialog()).getByRole("button", {name: "Save"}));

        fireEvent.click(screen.getByRole("button", {name: "Move requirement Confirm the answers are complete up"}));
        expect(screen.getByText("Unsaved changes")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Save draft"}));

        await waitFor(() => expect(editorCommands.save).toHaveBeenCalledTimes(1));
        const saved = vi.mocked(editorCommands.save).mock.calls[0][0];
        expect(saved.schemaVersionId).toBe("schema-version-1");
        expect(saved.sections).toHaveLength(1);
        expect(saved.sections[0]).toMatchObject({sectionKey: "collected-data", title: "Collected data"});
        expect(saved.sections[0].requirements.map(requirement => requirement.requirementKey)).toEqual([
            "recorded-summary",
            "completeness-confirmation",
            "attach-the-supporting-file",
        ]);
        expect(saved.sections[0].requirements[0]).toMatchObject({
            requirementType: InformationRequestRequirementType.FIELD,
            collectedFieldDefinitionId: "field-1",
            requiredness: InformationRequestRequiredness.REQUIRED,
        });
        expect(saved.sections[0].requirements[1].attestationPolicy?.requiredRoles)
            .toEqual([InformationRequestContributorRole.CONTRIBUTOR]);
        expect(saved.sections[0].requirements[2].evidencePolicy).toMatchObject({
            minimumFileCount: 1,
            maximumFileCount: 2,
            maximumFileSizeBytes: 5 * 1024 * 1024,
        });
    });

    it("authors a repeatable group, a condition, and a review stage that the requirements use", {timeout: 20000}, async () =>
    {
        const editorCommands = commands();
        const draft = template();
        draft.draftVersion = {
            ...template(false).latestPublishedVersion!,
            id: "draft-version-1",
            status: InformationRequestTemplateStatus.DRAFT
        };
        renderEditor(draft, editorCommands);

        fireEvent.click(screen.getByRole("tab", {name: "Groups"}));
        fireEvent.click(screen.getByRole("button", {name: "Add group"}));
        change("Key", "entry", dialog());
        change("Minimum entries", "1", dialog());
        change("Maximum entries", "3", dialog());
        fireEvent.click(within(dialog()).getByRole("button", {name: "Save"}));

        fireEvent.click(screen.getByRole("tab", {name: "Conditions"}));
        fireEvent.click(screen.getByRole("button", {name: "Add condition"}));
        change("Key", "when-summary-given", dialog());
        change("Reads", "requirement:recorded-summary", dialog());
        change("Answer", InformationRequestResponseDisposition.PROVIDED, dialog());
        fireEvent.click(within(dialog()).getByRole("button", {name: "Save"}));

        fireEvent.click(screen.getByRole("tab", {name: "Review"}));
        fireEvent.click(screen.getByRole("button", {name: "Add review stage"}));
        change("Title", "First check", dialog());
        change("Decision rule", "QUORUM", dialog());
        change("Minimum reviewers", "2", dialog());
        change("Quorum", "2", dialog());
        fireEvent.click(within(dialog()).getByRole("button", {name: "Save"}));

        fireEvent.click(screen.getByRole("tab", {name: "Sections"}));
        fireEvent.click(screen.getByRole("button", {name: "Edit requirement Provide the recorded summary"}));
        fireEvent.click(within(dialog()).getByRole("tab", {name: "Answering"}));
        change("Repeats", "entry", dialog());
        change("Review", InformationRequestReviewPolicy.REQUIRED, dialog());
        fireEvent.click(within(dialog()).getByRole("button", {name: "Save"}));
        fireEvent.click(screen.getByRole("button", {name: "Save draft"}));

        await waitFor(() => expect(editorCommands.save).toHaveBeenCalledTimes(1));
        const saved = vi.mocked(editorCommands.save).mock.calls[0][0];
        expect(saved.groups).toEqual([{groupKey: "entry", minOccurrences: 1, maxOccurrences: 3}]);
        expect(saved.conditionRules).toEqual([{
            ruleKey: "when-summary-given",
            expressionVersion: 1,
            hiddenDataPolicy: "RETAIN_SECURELY",
            predicates: [{
                operator: "EQUALS",
                sourceRequirementKey: "recorded-summary",
                expectedDisposition: InformationRequestResponseDisposition.PROVIDED,
            }],
        }]);
        expect(saved.reviewStages).toEqual([expect.objectContaining({
            stageKey: "first-check",
            title: "First check",
            aggregation: "QUORUM",
            quorumCount: 2,
            minimumReviewerCount: 2,
        })]);
        expect(saved.sections[0].requirements[0]).toMatchObject({
            occurrenceAnchorKey: "entry",
            reviewPolicy: InformationRequestReviewPolicy.REQUIRED,
        });
    });

    it("lists what stops publication and opens the requirement a problem is about", async () =>
    {
        renderEditor(template());

        fireEvent.click(screen.getByRole("button", {name: "Add section"}));
        change("Title", "Collected data", dialog());
        fireEvent.click(within(dialog()).getByRole("button", {name: "Save"}));
        fireEvent.click(screen.getByRole("button", {name: "Add requirement to Collected data"}));
        change("Prompt", "Provide the recorded summary", dialog());
        fireEvent.click(within(dialog()).getByRole("button", {name: "Save"}));

        const check = screen.getByRole("region", {name: "Before publishing"});
        expect(within(check).getByText("Choose the Request Schema that typed answers are recorded against.")).toBeTruthy();
        expect(screen.getByRole("button", {name: "Publish"}).hasAttribute("disabled")).toBe(true);

        fireEvent.click(within(check).getByRole("button", {
            name: "Requirement \"Provide the recorded summary\" needs the Field it collects.",
        }));

        expect(within(dialog()).getByLabelText("Collected Field")).toBeTruthy();
        await waitFor(() => expect(document.activeElement?.id).toBe("template-requirement-prompt-input"));
    });

    it("lists and checks the Fields of the earlier Schema Version a draft names", async () => {
        const earlier = schema().latestPublishedVersion!;
        const listed = schema();
        listed.latestPublishedVersion = {
            ...earlier,
            id: "schema-version-2",
            versionNumber: 2,
            bindings: [{
                ...earlier.bindings[0],
                id: "binding-2",
                fieldContractId: "field-contract-2",
                fieldDefinitionId: "field-2",
                fieldKey: "current-summary",
                label: "Current summary",
            }],
        };
        vi.mocked(getSchemaVersion).mockResolvedValue(earlier);
        const published = template(false).latestPublishedVersion!;
        const recorded = published.sections[0].requirements[0];
        const draft = template();
        draft.draftVersion = {
            ...published,
            id: "draft-version-1",
            status: InformationRequestTemplateStatus.DRAFT,
            schemaVersionId: "schema-version-1",
            sections: [{
                ...published.sections[0],
                requirements: [recorded, {
                    ...recorded,
                    id: "binding-row-2",
                    templateRequirementId: "requirement-2",
                    requirementKey: "current-summary",
                    prompt: "Provide the current summary",
                    collectedFieldDefinitionId: "field-2",
                }],
            }],
        };
        renderEditor(draft, commands(), true, [InformationRequestTemplateScopeKind.ORGANIZATION], [listed]);

        const check = screen.getByRole("region", {name: "Before publishing"});
        expect(await within(check).findByText(
            "Requirement \"Provide the current summary\" collects a Field the chosen Request Schema does not ask for.",
        )).toBeTruthy();
        expect(getSchemaVersion).toHaveBeenCalledWith("schema-version-1");
        fireEvent.click(screen.getByRole("button", {name: "Edit requirement Provide the recorded summary"}));
        const field = within(dialog()).getByLabelText("Collected Field") as HTMLSelectElement;
        expect(field.value).toBe("field-1");
        expect(within(field).getAllByRole("option").map(option => option.textContent)).toEqual(["Choose a Field", "Recorded summary"]);
    });

    it("shows a server refusal beside the requirement it names", async () =>
    {
        const editorCommands = commands();
        vi.mocked(editorCommands.publish).mockRejectedValue({
            errorMessage: "Requirement recorded-summary asks for typed data, so this version names the Schema Version it resolves against",
            reasonCode: "INFORMATION_REQUEST_TEMPLATE_INVALID",
            sectionKey: "collected-data",
            requirementKey: "recorded-summary",
        });
        const draft = template();
        draft.draftVersion = {
            ...template(false).latestPublishedVersion!,
            id: "draft-version-1",
            status: InformationRequestTemplateStatus.DRAFT
        };
        renderEditor(draft, editorCommands);

        fireEvent.click(screen.getByRole("button", {name: "Publish"}));

        const refusal = await screen.findByRole("alert");
        expect(within(refusal).getByText(/asks for typed data/)).toBeTruthy();
        fireEvent.click(within(refusal).getByRole("button", {name: "Show"}));
        expect(within(dialog()).getByDisplayValue("Provide the recorded summary")).toBeTruthy();
    });

    it("explains that the draft changed after it was opened instead of overwriting it", async () => {
        const editorCommands = commands();
        vi.mocked(editorCommands.publish).mockRejectedValue({
            errorMessage: "The data changed after it was read; reload it and try again",
            reasonCode: "COMMAND_PRECONDITION_STALE",
        });
        const draft = template();
        draft.draftVersion = {
            ...template(false).latestPublishedVersion!,
            id: "draft-version-1",
            status: InformationRequestTemplateStatus.DRAFT
        };
        renderEditor(draft, editorCommands);

        fireEvent.click(screen.getByRole("button", {name: "Publish"}));

        const refusal = await screen.findByRole("alert");
        expect(within(refusal).getByText(
            "This draft was changed after you opened it, so your change was not made. " +
            "Select Open latest draft to continue from it.",
        )).toBeTruthy();
        expect(within(refusal).queryByRole("button", {name: "Keep my changes"})).toBeNull();
    });

    it("states a failed read of the chosen Schema Version's Fields and reads it again on Try again", async () => {
        const draft = template();
        draft.draftVersion = {...draft.draftVersion!, schemaVersionId: "schema-version-unlisted"};
        vi.mocked(getSchemaVersion)
            .mockRejectedValueOnce({errorMessage: "Request failed"})
            .mockResolvedValueOnce({...schema().latestPublishedVersion!, id: "schema-version-unlisted"});
        renderEditor(draft);

        const failure = await screen.findByRole("alert");
        expect(within(failure).getByText("The Fields of this Template's Schema Version could not be loaded.")).toBeTruthy();
        fireEvent.click(within(failure).getByRole("button", {name: "Try again"}));

        await waitFor(() => expect(screen.queryByText("The Fields of this Template's Schema Version could not be loaded.")).toBeNull());
        expect(getSchemaVersion).toHaveBeenCalledTimes(2);
    });

    it("offers no Show for a refusal that names no part of the Template", async () => {
        const editorCommands = commands();
        vi.mocked(editorCommands.save).mockRejectedValueOnce({
            errorMessage: "Access denied to information request template configuration",
            reasonCode: "INFORMATION_REQUEST_FORBIDDEN",
        });
        renderEditor(template(), editorCommands);
        chooseSchema();

        fireEvent.click(screen.getByRole("button", {name: "Save draft"}));

        const refusal = await screen.findByRole("alert");
        expect(within(refusal).getByText("Access denied to information request template configuration")).toBeTruthy();
        expect(within(refusal).queryByRole("button", {name: "Show"})).toBeNull();
    });

    it("keeps unsaved changes through a stale save and saves them over the latest draft on request", async () => {
        const editorCommands = commands();
        vi.mocked(editorCommands.save).mockRejectedValueOnce(STALE_REFUSAL);
        renderEditor(template(), editorCommands);
        chooseSchema();

        fireEvent.click(screen.getByRole("button", {name: "Save draft"}));
        const refusal = await screen.findByRole("alert");
        expect(within(refusal).getByText(
            "This draft was changed after you opened it, so your changes were not saved. Select Keep my changes " +
            "to save them over the latest draft, or Open latest draft to discard them.",
        )).toBeTruthy();
        fireEvent.click(within(refusal).getByRole("button", {name: "Keep my changes"}));

        await waitFor(() => expect(editorCommands.save).toHaveBeenCalledTimes(2));
        expect(editorCommands.adoptLatestDraft).toHaveBeenCalledTimes(1);
        expect(vi.mocked(editorCommands.save).mock.calls[1][0].schemaVersionId).toBe("schema-version-1");
        expect(editorCommands.reload).not.toHaveBeenCalled();
    });

    it("opens the latest draft instead when the author chooses it after a stale save", async () => {
        const editorCommands = commands();
        vi.mocked(editorCommands.save).mockRejectedValueOnce(STALE_REFUSAL);
        renderEditor(template(), editorCommands);
        chooseSchema();

        fireEvent.click(screen.getByRole("button", {name: "Save draft"}));
        fireEvent.click(within(await screen.findByRole("alert")).getByRole("button", {name: "Open latest draft"}));

        await waitFor(() => expect(editorCommands.reload).toHaveBeenCalledTimes(1));
        expect(editorCommands.save).toHaveBeenCalledTimes(1);
    });

    it("asks before discarding unsaved changes when going back to Templates", async () => {
        const onClose = vi.fn();
        render(<TemplateEditor template={template()}
                               schemas={[schema()]}
                               schemasError={null}
                               onRetrySchemas={vi.fn()}
                               canManage={true}
                               copyTargets={[]}
                               commands={commands()}
                               onClose={onClose}/>);
        chooseSchema();

        fireEvent.click(screen.getByRole("button", {name: "Back to Templates"}));
        expect(within(dialog()).getByText("Discard unsaved changes?")).toBeTruthy();
        fireEvent.click(within(dialog()).getByRole("button", {name: "Keep editing"}));
        await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
        expect(onClose).not.toHaveBeenCalled();
        expect(screen.getByText("Unsaved changes")).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Back to Templates"}));
        fireEvent.click(within(dialog()).getByRole("button", {name: "Discard changes"}));
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("goes back to Templates at once when nothing is unsaved", () => {
        const onClose = vi.fn();
        render(<TemplateEditor template={template()}
                               schemas={[schema()]}
                               schemasError={null}
                               onRetrySchemas={vi.fn()}
                               canManage={true}
                               copyTargets={[]}
                               commands={commands()}
                               onClose={onClose}/>);

        fireEvent.click(screen.getByRole("button", {name: "Back to Templates"}));

        expect(onClose).toHaveBeenCalledTimes(1);
        expect(screen.queryByRole("dialog")).toBeNull();
    });

    it("shows the content hash that configuration bundles name a published Version by", () => {
        const published = template(false);
        published.latestPublishedVersion = {...published.latestPublishedVersion!, contentHashSha256: "a".repeat(64)};
        renderEditor(published, commands(), false);

        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));

        expect(document.getElementById("information-request-template-versions-content-hash")?.textContent)
            .toBe(`Content hash ${"a".repeat(64)}`);
        expect(document.getElementById("information-request-template-versions-published-at")?.textContent)
            .toBe(`Published on ${formatInformationRequestTime(Date.parse("2026-09-03T00:00:00Z"))}. ` +
                "Requests already created keep the version they started with.");
    });

    it("opens a published Version read-only and starts, retires, and copies from it", async () =>
    {
        const editorCommands = renderEditor(template(false));

        expect(screen.queryByRole("button", {name: "Save draft"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Add section"})).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "View requirement Provide the recorded summary"}));
        expect((within(dialog()).getByLabelText("Prompt") as HTMLInputElement).disabled).toBe(true);
        fireEvent.click(within(dialog()).getByRole("button", {name: "Close"}));

        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));
        fireEvent.click(screen.getByRole("button", {name: "Start a new draft from version 1"}));
        await waitFor(() => expect(editorCommands.startDraft).toHaveBeenCalledWith(1));

        fireEvent.click(screen.getByRole("button", {name: "Retire version 1"}));
        fireEvent.click(within(dialog()).getByRole("button", {name: "Retire"}));
        await waitFor(() => expect(editorCommands.retire).toHaveBeenCalledWith(1));

        fireEvent.click(screen.getByRole("button", {name: "Copy as a new Template"}));
        change("Name", "Second pattern", dialog());
        fireEvent.click(within(dialog()).getByRole("button", {name: "Copy"}));
        await waitFor(() => expect(editorCommands.clone).toHaveBeenCalledWith(1, {
            namespace: "process",
            templateKey: "second-pattern",
            displayName: "Second pattern",
            scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION,
        }));
    });

    it("acts on the published Version the author chooses, not only the newest", async () => {
        const current = template(false);
        current.latestPublishedVersion = {...current.latestPublishedVersion!, versionNumber: 2};
        current.versions = [
            {
                versionNumber: 1,
                status: InformationRequestTemplateStatus.PUBLISHED,
                publishedAt: Date.parse("2026-09-01T00:00:00Z")
            },
            {
                versionNumber: 2,
                status: InformationRequestTemplateStatus.PUBLISHED,
                publishedAt: Date.parse("2026-09-03T00:00:00Z")
            },
        ];
        const editorCommands = renderEditor(current);
        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));

        change("Version to act on", "1");
        fireEvent.click(screen.getByRole("button", {name: "Retire version 1"}));
        expect(within(dialog()).queryByText(/only published version/)).toBeNull();
        fireEvent.click(within(dialog()).getByRole("button", {name: "Retire"}));

        await waitFor(() => expect(editorCommands.retire).toHaveBeenCalledWith(1));
    });

    it("opens an earlier published Version read-only from the Versions tab and returns to the current one", async () => {
        vi.mocked(getInformationRequestTemplateVersion)
            .mockResolvedValue(earlierVersion(1, InformationRequestTemplateStatus.PUBLISHED, "Provide the earlier summary"));
        renderEditor(twoVersions());
        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));
        expect(screen.queryByRole("button", {name: "View version 2"})).toBeNull();

        fireEvent.click(screen.getByRole("button", {name: "View version 1"}));

        expect(await screen.findByText("Viewing version 1, published")).toBeTruthy();
        expect(getInformationRequestTemplateVersion).toHaveBeenCalledWith("template-1", 1);
        fireEvent.click(screen.getByRole("tab", {name: "Sections"}));
        expect(screen.getByRole("button", {name: "View requirement Provide the earlier summary"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Save draft"})).toBeNull();

        fireEvent.click(screen.getByRole("button", {name: "Show current version"}));

        expect(await screen.findByText("Published version 2")).toBeTruthy();
        expect(screen.getByRole("button", {name: "View requirement Provide the recorded summary"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Show current version"})).toBeNull();
    });

    it("shows a retired Version read-only when chosen while the Versions tab still acts on the published one", async () => {
        vi.mocked(getInformationRequestTemplateVersion)
            .mockResolvedValue(earlierVersion(1, InformationRequestTemplateStatus.RETIRED, "Provide the withdrawn summary"));
        renderEditor(twoVersions(InformationRequestTemplateStatus.RETIRED));
        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));

        fireEvent.click(screen.getByRole("button", {name: "View version 1"}));

        expect(await screen.findByText("Viewing version 1, retired")).toBeTruthy();
        expect(screen.getByRole("button", {name: "Retire version 2"})).toBeTruthy();
        fireEvent.click(screen.getByRole("tab", {name: "Sections"}));
        expect(screen.getByRole("button", {name: "View requirement Provide the withdrawn summary"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Add section"})).toBeNull();
    });

    it("opens the latest retired Version read-only when every Version is retired instead of an empty editor", async () => {
        const current = template(false);
        current.status = InformationRequestTemplateStatus.RETIRED;
        current.latestPublishedVersion = undefined;
        current.versions = [
            {
                versionNumber: 1,
                status: InformationRequestTemplateStatus.RETIRED,
                retiredAt: Date.parse("2026-09-05T00:00:00Z")
            },
            {
                versionNumber: 2,
                status: InformationRequestTemplateStatus.RETIRED,
                retiredAt: Date.parse("2026-09-09T00:00:00Z")
            },
        ];
        vi.mocked(getInformationRequestTemplateVersion)
            .mockResolvedValue(earlierVersion(2, InformationRequestTemplateStatus.RETIRED, "Provide the final summary"));

        renderEditor(current);

        expect(await screen.findByText("All versions retired, viewing version 2")).toBeTruthy();
        expect(getInformationRequestTemplateVersion).toHaveBeenCalledWith("template-1", 2);
        expect(screen.getByRole("button", {name: "View requirement Provide the final summary"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Show current version"})).toBeNull();
        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));
        expect(screen.getByRole("button", {name: "View version 1"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: "View version 2"})).toBeNull();
    });

    it("states a failed Version read with Try again and shows the Version once it loads", async () => {
        vi.mocked(getInformationRequestTemplateVersion)
            .mockRejectedValueOnce({errorMessage: "This Version could not be read."})
            .mockResolvedValueOnce(earlierVersion(1, InformationRequestTemplateStatus.PUBLISHED, "Provide the earlier summary"));
        renderEditor(twoVersions());
        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));

        fireEvent.click(screen.getByRole("button", {name: "View version 1"}));

        await waitFor(() => expect(document.getElementById("information-request-template-version-load-error")?.textContent)
            .toContain("This Version could not be read."));
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        expect(await screen.findByText("Viewing version 1, published")).toBeTruthy();
        expect(document.getElementById("information-request-template-version-load-error")).toBeNull();
    });

    it("says that retiring the only published Version with no draft open ends the Template", () => {
        renderEditor(template(false));
        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));

        fireEvent.click(screen.getByRole("button", {name: "Retire version 1"}));

        expect(within(dialog()).getByText(
            "Version 1 is the only published version and no draft is open, so retiring it ends this Template: " +
            "no request, new draft or copy can start from it again.",
        )).toBeTruthy();
    });

    it("lists retired Versions and names a Template whose Versions are all retired", async () => {
        const current = template(false);
        current.latestPublishedVersion = undefined;
        current.status = InformationRequestTemplateStatus.RETIRED;
        current.versions = [{
            versionNumber: 1,
            status: InformationRequestTemplateStatus.RETIRED,
            publishedAt: Date.parse("2026-09-03T00:00:00Z"),
            retiredAt: Date.parse("2026-09-04T00:00:00Z"),
        }];
        vi.mocked(getInformationRequestTemplateVersion)
            .mockResolvedValue(earlierVersion(1, InformationRequestTemplateStatus.RETIRED, "Provide the recorded summary"));
        renderEditor(current);

        expect(await screen.findByText("All versions retired, viewing version 1")).toBeTruthy();
        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));
        expect(document.getElementById("information-request-template-version-1-standing")?.textContent)
            .toBe(`Version 1, retired on ${formatInformationRequestTime(Date.parse("2026-09-04T00:00:00Z"))}`);
        expect(document.getElementById("information-request-template-version-1-shown")?.textContent).toBe("Shown");
        expect(screen.queryByRole("button", {name: "View version 1"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Retire version 1"})).toBeNull();
    });

    it("carries the description into a copy and states the Schema rule for a copy owned elsewhere", async () => {
        const current = {...template(false), description: "Collect one typed response"};
        const editorCommands = renderEditor(current, commands(), true, [
            InformationRequestTemplateScopeKind.ORGANIZATION,
            InformationRequestTemplateScopeKind.PERSONAL,
        ]);
        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));
        fireEvent.click(screen.getByRole("button", {name: "Copy as a new Template"}));

        expect((within(dialog()).getByLabelText("Description") as HTMLTextAreaElement).value).toBe("Collect one typed response");
        expect(within(dialog()).queryByText(/Platform Schema/)).toBeNull();
        change("Copy into", InformationRequestTemplateScopeKind.PERSONAL, dialog());
        expect(within(dialog()).getByText(
            "Typed answers keep the Schema this version names. A copy for another owner can keep it only when it is " +
            "a Platform Schema; otherwise the copy is refused.",
        )).toBeTruthy();
        change("Name", "Second pattern", dialog());
        fireEvent.click(within(dialog()).getByRole("button", {name: "Copy"}));

        await waitFor(() => expect(editorCommands.clone).toHaveBeenCalledWith(1, {
            namespace: "process",
            templateKey: "second-pattern",
            displayName: "Second pattern",
            description: "Collect one typed response",
            scopeKind: InformationRequestTemplateScopeKind.PERSONAL,
        }));
    });

    it("offers no copy of a Template that has never been published", () => {
        renderEditor(template(true));

        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));

        expect(screen.queryByRole("button", {name: "Copy as a new Template"})).toBeNull();
    });

    it("states a refused retirement inside the dialog that asked for it and keeps the dialog open", async () => {
        const editorCommands = commands();
        vi.mocked(editorCommands.retire).mockRejectedValueOnce({errorMessage: "Version 1 is already retired"});
        renderEditor(template(false), editorCommands);
        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));

        fireEvent.click(screen.getByRole("button", {name: "Retire version 1"}));
        expect(within(dialog()).queryByRole("alert")).toBeNull();
        fireEvent.click(within(dialog()).getByRole("button", {name: "Retire"}));

        expect(await within(dialog()).findByText("Version 1 is already retired")).toBeTruthy();
        expect(within(dialog()).getByRole("button", {name: "Retire"})).toBeTruthy();
    });

    it("states a refused copy inside the dialog that asked for it and keeps what was entered", async () => {
        const editorCommands = commands();
        vi.mocked(editorCommands.clone).mockRejectedValueOnce({errorMessage: "The key process:second-pattern is already used"});
        renderEditor(template(false), editorCommands);
        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));

        fireEvent.click(screen.getByRole("button", {name: "Copy as a new Template"}));
        change("Name", "Second pattern", dialog());
        fireEvent.click(within(dialog()).getByRole("button", {name: "Copy"}));

        expect(await within(dialog()).findByText("The key process:second-pattern is already used")).toBeTruthy();
        expect((within(dialog()).getByLabelText("Name") as HTMLInputElement).value).toBe("Second pattern");
    });

    it("lets a reader who authors elsewhere copy a Template without changing it", () =>
    {
        renderEditor(template(false), commands(), false, [InformationRequestTemplateScopeKind.PERSONAL]);

        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));

        expect(screen.queryByRole("button", {name: "Start a new draft from version 1"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Retire version 1"})).toBeNull();
        expect(screen.getByRole("button", {name: "Copy as a new Template"})).toBeTruthy();
    });

    it("states the Schema rule to a member whose only place to copy into is owned elsewhere", () => {
        renderEditor(template(false), commands(), false, [InformationRequestTemplateScopeKind.PERSONAL]);
        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));
        fireEvent.click(screen.getByRole("button", {name: "Copy as a new Template"}));

        expect(within(dialog()).queryByLabelText("Copy into")).toBeNull();
        expect(within(dialog()).getByText(
            "Typed answers keep the Schema this version names. A copy for another owner can keep it only when it is " +
            "a Platform Schema; otherwise the copy is refused.",
        )).toBeTruthy();
    });

    it("never offers lifecycle commands to a reader who cannot manage the Template", () =>
    {
        renderEditor(template(false), commands(), false);

        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));

        expect(screen.queryByRole("button", {name: "Start a new draft from version 1"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Retire version 1"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Copy as a new Template"})).toBeNull();
        expect(document.getElementById("information-request-template-versions-published")?.textContent)
            .toBe("Published version 1");
    });
});
