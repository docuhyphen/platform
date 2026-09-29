/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    CurrentSessionDto,
    FieldScopeKind,
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
} from "../../models/models.tsx";
import InformationRequestTemplatesTab from "./InformationRequestTemplatesTab.tsx";

const templateApi = vi.hoisted(() => ({
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    replace: vi.fn(),
    publish: vi.fn(),
    newVersion: vi.fn(),
    retire: vi.fn(),
    clone: vi.fn(),
}));

const fieldApi = vi.hoisted(() => ({
    listSchemas: vi.fn(),
}));

const authState = vi.hoisted(() => ({
    currentSession: {
        activeOrganizationId: "organization-1",
    } as CurrentSessionDto,
    canManageOrganization: true,
}));

vi.mock("../../../services/informationRequestTemplateService.ts", () => ({
    listInformationRequestTemplates: (...args: unknown[]) => templateApi.list(...args),
    getInformationRequestTemplate: (...args: unknown[]) => templateApi.get(...args),
    createInformationRequestTemplate: (...args: unknown[]) => templateApi.create(...args),
    replaceInformationRequestTemplateDraftConfiguration: (...args: unknown[]) => templateApi.replace(...args),
    publishInformationRequestTemplateDraft: (...args: unknown[]) => templateApi.publish(...args),
    createInformationRequestTemplateDraftVersion: (...args: unknown[]) => templateApi.newVersion(...args),
    retireInformationRequestTemplateVersion: (...args: unknown[]) => templateApi.retire(...args),
    cloneInformationRequestTemplate: (...args: unknown[]) => templateApi.clone(...args),
}));

vi.mock("../../../services/fieldsService.ts", () => ({
    listSchemas: (...args: unknown[]) => fieldApi.listSchemas(...args),
}));

vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        currentSession: authState.currentSession,
        hasCapability: () => authState.canManageOrganization,
    }),
}));

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

const templateSummary = {
    id: "template-1",
    scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION,
    namespace: "process",
    templateKey: "collection-pattern",
    displayName: "Collection pattern",
    description: "Collect one typed response",
    status: InformationRequestTemplateStatus.DRAFT,
    hasEditableVersion: true,
    createdAt: "2026-09-02T00:00:00Z",
    updatedAt: "2026-09-02T00:00:00Z",
};

const draftTemplate = (updatedAt = "2026-09-02T00:00:00Z"): InformationRequestTemplateDto => ({
    ...templateSummary,
    updatedAt,
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
});

const dialog = () => screen.getByRole("dialog");

describe("InformationRequestTemplatesTab", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        templateApi.list.mockResolvedValue([templateSummary]);
        templateApi.get.mockResolvedValue(draftTemplate());
        templateApi.create.mockResolvedValue(draftTemplate());
        templateApi.replace.mockResolvedValue(draftTemplate("2026-09-03T00:00:00Z"));
        templateApi.publish.mockResolvedValue({
            ...draftTemplate(),
            status: InformationRequestTemplateStatus.PUBLISHED,
        });
        fieldApi.listSchemas.mockResolvedValue([]);
        authState.currentSession = {
            activeOrganizationId: "organization-1",
        } as CurrentSessionDto;
        authState.canManageOrganization = true;
    });

    it("lists Templates, opens one in the editor, saves it against its own id, and returns to the list", async () =>
    {
        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("Collection pattern")).toBeTruthy();
        expect(templateApi.list).toHaveBeenCalledWith({scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION});
        expect(fieldApi.listSchemas).toHaveBeenCalledWith({scopeKind: FieldScopeKind.ORGANIZATION});
        expect(fieldApi.listSchemas).toHaveBeenCalledWith({scopeKind: FieldScopeKind.PLATFORM});

        fireEvent.click(screen.getByRole("button", {name: "Open Collection pattern"}));
        expect(await screen.findByRole("button", {name: "Back to Templates"})).toBeTruthy();
        expect(templateApi.get).toHaveBeenCalledWith("template-1");

        fireEvent.click(screen.getByRole("button", {name: "Add section"}));
        fireEvent.change(within(dialog()).getByLabelText("Title"), {target: {value: "Collected data"}});
        fireEvent.click(within(dialog()).getByRole("button", {name: "Save"}));
        fireEvent.click(screen.getByRole("button", {name: "Save draft"}));

        await waitFor(() => expect(templateApi.replace).toHaveBeenCalledWith("template-1", expect.objectContaining({
            sections: [expect.objectContaining({sectionKey: "collected-data", title: "Collected data"})],
        })));
        expect(await screen.findByText("All changes saved")).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Back to Templates"}));
        expect(await screen.findByRole("button", {name: "Open Collection pattern"})).toBeTruthy();
    });

    it("creates a Template for the active organization under a key taken from its name", async () =>
    {
        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("Collection pattern")).toBeTruthy();
        expect(screen.getByRole("tab", {name: "Organization"}).getAttribute("aria-selected")).toBe("true");
        fireEvent.click(screen.getByRole("button", {name: "New Template"}));
        fireEvent.change(within(dialog()).getByLabelText("Name"), {target: {value: "Evidence Collection"}});
        fireEvent.change(within(dialog()).getByLabelText("Description"), {target: {value: "Collect files"}});
        fireEvent.click(within(dialog()).getByRole("button", {name: "Create"}));

        await waitFor(() => expect(templateApi.create).toHaveBeenCalledWith({
            namespace: "process",
            templateKey: "evidence-collection",
            displayName: "Evidence Collection",
            description: "Collect files",
            scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION,
        }));
        expect(await screen.findByRole("button", {name: "Back to Templates"})).toBeTruthy();
    });

    it("keeps Platform Templates readable but closed to authoring", async () =>
    {
        templateApi.get.mockResolvedValue({
            ...draftTemplate(),
            scopeKind: InformationRequestTemplateScopeKind.PLATFORM,
        });
        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("Collection pattern")).toBeTruthy();
        fireEvent.click(screen.getByRole("tab", {name: "Platform"}));

        await waitFor(() => expect(templateApi.list).toHaveBeenLastCalledWith({
            scopeKind: InformationRequestTemplateScopeKind.PLATFORM,
        }));
        expect(screen.queryByRole("button", {name: "New Template"})).toBeNull();
        fireEvent.click(await screen.findByRole("button", {name: "Open Collection pattern"}));
        expect(await screen.findByText("Read only")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Save draft"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Add section"})).toBeNull();
    });

    it("keeps Personal Templates as a separate owner scope", async () =>
    {
        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("Collection pattern")).toBeTruthy();
        fireEvent.click(screen.getByRole("tab", {name: "My Templates"}));

        await waitFor(() => expect(templateApi.list).toHaveBeenLastCalledWith({
            scopeKind: InformationRequestTemplateScopeKind.PERSONAL,
        }));
        expect(fieldApi.listSchemas).toHaveBeenCalledWith({scopeKind: FieldScopeKind.PERSONAL});
        expect(screen.getByRole("button", {name: "New Template"})).toBeTruthy();
    });

    it("lets organization members read Templates without authoring controls", async () =>
    {
        authState.canManageOrganization = false;
        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("Collection pattern")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "New Template"})).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "Open Collection pattern"}));
        expect(await screen.findByText("Read only")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Save draft"})).toBeNull();
    });

    it("says why the list could not be read", async () =>
    {
        templateApi.list.mockRejectedValue({errorMessage: "Denied", reasonCode: "FEATURE_NOT_INCLUDED"});
        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("Information Request Templates could not be loaded.")).toBeTruthy();
    });
});
