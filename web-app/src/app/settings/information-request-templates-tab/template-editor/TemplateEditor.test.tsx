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
    SchemaDefinitionDto,
} from "../../../models/models.tsx";
import TemplateEditor from "./TemplateEditor.tsx";
import {TemplateEditorCommands} from "./templateEditorTypes.ts";

beforeAll(() =>
{
    globalThis.ResizeObserver = class
    {
        observe() {}
        unobserve() {}
        disconnect() {}
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
        createdAt: "2026-09-02T00:00:00Z",
    },
    createdAt: "2026-09-02T00:00:00Z",
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
        createdAt: "2026-09-02T00:00:00Z",
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
        publishedAt: "2026-09-03T00:00:00Z",
        createdAt: "2026-09-02T00:00:00Z",
    },
    createdAt: "2026-09-02T00:00:00Z",
    updatedAt: "2026-09-02T00:00:00Z",
});

const commands = (): TemplateEditorCommands => ({
    save: vi.fn(async () => template()),
    publish: vi.fn(async () => template()),
    startDraft: vi.fn(async () => template()),
    retire: vi.fn(async () => template(false)),
    clone: vi.fn(async () => template()),
});

const renderEditor = (
    current: InformationRequestTemplateDto,
    editorCommands = commands(),
    canManage = true,
    copyTargets: InformationRequestTemplateScopeKind[] = canManage ? [InformationRequestTemplateScopeKind.ORGANIZATION] : [],
) =>
{
    render(<TemplateEditor template={current}
                           schemas={[schema()]}
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
        change("Maximum file size (MB)", "5", dialog());
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
        draft.draftVersion = {...template(false).latestPublishedVersion!, id: "draft-version-1", status: InformationRequestTemplateStatus.DRAFT};
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
        draft.draftVersion = {...template(false).latestPublishedVersion!, id: "draft-version-1", status: InformationRequestTemplateStatus.DRAFT};
        renderEditor(draft, editorCommands);

        fireEvent.click(screen.getByRole("button", {name: "Publish"}));

        const refusal = await screen.findByRole("alert");
        expect(within(refusal).getByText(/asks for typed data/)).toBeTruthy();
        fireEvent.click(within(refusal).getByRole("button", {name: "Show"}));
        expect(within(dialog()).getByDisplayValue("Provide the recorded summary")).toBeTruthy();
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

    it("lets a reader who authors elsewhere copy a Template without changing it", () =>
    {
        renderEditor(template(false), commands(), false, [InformationRequestTemplateScopeKind.PERSONAL]);

        fireEvent.click(screen.getByRole("tab", {name: "Versions"}));

        expect(screen.queryByRole("button", {name: "Start a new draft from version 1"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Retire version 1"})).toBeNull();
        expect(screen.getByRole("button", {name: "Copy as a new Template"})).toBeTruthy();
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
