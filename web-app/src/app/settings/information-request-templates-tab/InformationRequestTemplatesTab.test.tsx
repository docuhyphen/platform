/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {
    FieldDataClassification,
    FieldLifecycleStatus,
    FieldScopeKind,
    FieldValueType,
    InformationRequestRequirementType,
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    SchemaDefinitionDto,
} from "../../models/models.tsx";
import InformationRequestTemplatesTab from "./InformationRequestTemplatesTab.tsx";

const templateApi = vi.hoisted(() => ({
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    replace: vi.fn(),
    publish: vi.fn(),
}));

const fieldApi = vi.hoisted(() => ({
    listSchemas: vi.fn(),
}));

vi.mock("../../../services/informationRequestTemplateService.ts", () => ({
    listInformationRequestTemplates: (...args: unknown[]) => templateApi.list(...args),
    getInformationRequestTemplate: (...args: unknown[]) => templateApi.get(...args),
    createInformationRequestTemplate: (...args: unknown[]) => templateApi.create(...args),
    replaceInformationRequestTemplateDraftConfiguration: (...args: unknown[]) => templateApi.replace(...args),
    publishInformationRequestTemplateDraft: (...args: unknown[]) => templateApi.publish(...args),
}));

vi.mock("../../../services/fieldsService.ts", () => ({
    listSchemas: (...args: unknown[]) => fieldApi.listSchemas(...args),
}));

vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        appUserPersonOrganization: {isActive: true},
    }),
}));

const templateSummary = {
    id: "template-1",
    scopeKind: InformationRequestTemplateScopeKind.PERSONAL,
    namespace: "process",
    templateKey: "collection-pattern",
    displayName: "Collection pattern",
    description: "Collect one typed response",
    status: InformationRequestTemplateStatus.DRAFT,
    hasEditableVersion: true,
    createdAt: "2026-09-02T00:00:00Z",
    updatedAt: "2026-09-02T00:00:00Z",
};

const draftTemplate = (): InformationRequestTemplateDto => ({
    ...templateSummary,
    draftVersion: {
        id: "draft-version-1",
        templateDefinitionId: "template-1",
        versionNumber: 1,
        status: InformationRequestTemplateStatus.DRAFT,
        sections: [],
        groups: [],
        conditionRules: [],
        requiredCapabilities: [],
        createdAt: "2026-09-02T00:00:00Z",
    },
    unsupportedPolicyControls: [{
        controlKey: "document-evidence-policy",
        label: "Document Evidence Policy",
        reason: "Document evidence policy controls are not available in this deployment.",
    }],
} as InformationRequestTemplateDto);

const requestSchema = (): SchemaDefinitionDto => ({
    id: "schema-1",
    scopeKind: FieldScopeKind.PERSONAL,
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
        publishedAt: "2026-09-02T00:00:00Z",
        createdAt: "2026-09-02T00:00:00Z",
    },
    createdAt: "2026-09-02T00:00:00Z",
});

afterEach(cleanup);

describe("InformationRequestTemplatesTab", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        templateApi.list.mockResolvedValue([templateSummary]);
        templateApi.get.mockResolvedValue(draftTemplate());
        templateApi.create.mockResolvedValue(draftTemplate());
        templateApi.replace.mockResolvedValue(draftTemplate());
        templateApi.publish.mockResolvedValue({
            ...draftTemplate(),
            status: InformationRequestTemplateStatus.PUBLISHED,
        });
        fieldApi.listSchemas.mockResolvedValue([requestSchema()]);
    });

    it("lists Templates, saves one typed draft Requirement, and publishes", async () =>
    {
        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("Collection pattern")).toBeTruthy();
        expect(templateApi.list).toHaveBeenCalledWith({
            scopeKind: InformationRequestTemplateScopeKind.PERSONAL,
        });

        fireEvent.click(screen.getByRole("button", {name: "Open draft"}));

        expect(await screen.findByText("Document evidence policy controls are not available in this deployment."))
            .toBeTruthy();
        expect(screen.getByRole("button", {name: "Document Evidence Policy"}).hasAttribute("disabled"))
            .toBe(true);

        fireEvent.change(document.querySelector("#information-request-template-requirement-key-input")!, {
            target: {value: "recorded-summary"},
        });
        fireEvent.change(document.querySelector("#information-request-template-requirement-prompt-input")!, {
            target: {value: "Provide the recorded summary"},
        });
        fireEvent.click(screen.getByRole("button", {name: "Save draft"}));

        await waitFor(() => expect(templateApi.replace).toHaveBeenCalledWith(
            "template-1",
            {
                schemaVersionId: "schema-version-1",
                sections: [{
                    sectionKey: "requested-data",
                    title: "Requested data",
                    requirements: [{
                        requirementKey: "recorded-summary",
                        requirementType: InformationRequestRequirementType.FIELD,
                        prompt: "Provide the recorded summary",
                        collectedFieldDefinitionId: "field-1",
                    }],
                }],
            },
        ));

        fireEvent.click(screen.getByRole("button", {name: "Publish draft"}));

        await waitFor(() => expect(templateApi.publish).toHaveBeenCalledWith("template-1"));
    });
});
