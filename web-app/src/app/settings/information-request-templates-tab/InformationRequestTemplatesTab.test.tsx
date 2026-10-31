/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    Capability,
    CurrentSessionDto,
    FieldScopeKind,
    InformationRequestStandingReason,
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    PlanCode,
    SubscriptionOwnerType,
} from "../../models/models.tsx";
import {
    useInformationRequestCapabilities
} from "../../information-requests/capabilities/useInformationRequestCapabilities.ts";
import {
    capabilitiesWithoutTheFeature,
    informationRequestCapabilities,
} from "../../information-requests/shared/testing/capabilityFixtures.ts";
import {unnamedControls} from "../../information-requests/shared/testing/unnamedControls.ts";
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
    platformAdministrator: false,
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

vi.mock("../../information-requests/capabilities/useInformationRequestCapabilities.ts", () => ({
    useInformationRequestCapabilities: vi.fn(),
}));

vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        currentSession: authState.currentSession,
        hasCapability: (capability: Capability) => capability === Capability.APP_ADMIN
            ? authState.platformAdministrator
            : authState.canManageOrganization,
    }),
}));

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

const templateSummary = {
    id: "template-1",
    scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION,
    namespace: "process",
    templateKey: "collection-pattern",
    displayName: "Collection pattern",
    description: "Collect one typed response",
    status: InformationRequestTemplateStatus.DRAFT,
    hasEditableVersion: true,
    createdAt: Date.parse("2026-09-02T00:00:00Z"),
    updatedAt: Date.parse("2026-09-02T00:00:00Z"),
};

const draftTemplate = (savedAt = "2026-09-02T00:00:00Z"): InformationRequestTemplateDto => ({
    ...templateSummary,
    updatedAt: Date.parse(savedAt),
    draftETag: `"draft-version-1:${savedAt}"`,
    draftVersion: {
        id: "draft-version-1",
        templateDefinitionId: "template-1",
        versionNumber: 1,
        status: InformationRequestTemplateStatus.DRAFT,
        sections: [],
        groups: [],
        conditionRules: [],
        requiredCapabilities: [],
        createdAt: Date.parse("2026-09-02T00:00:00Z"),
    },
});

const publishedTemplate = (): InformationRequestTemplateDto => ({
    ...templateSummary,
    status: InformationRequestTemplateStatus.PUBLISHED,
    latestPublishedVersion: {
        id: "published-version-1",
        templateDefinitionId: "template-1",
        versionNumber: 1,
        status: InformationRequestTemplateStatus.PUBLISHED,
        sections: [],
        groups: [],
        conditionRules: [],
        requiredCapabilities: [],
        publishedAt: Date.parse("2026-09-03T00:00:00Z"),
        createdAt: Date.parse("2026-09-02T00:00:00Z"),
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
        authState.platformAdministrator = false;
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(informationRequestCapabilities({
            ownerType: SubscriptionOwnerType.ORGANIZATION,
            planCode: PlanCode.BUSINESS,
        }));
    });

    it("lists Templates, opens one in the editor, saves it against its own id, and returns to the list", {timeout: 20000}, async () =>
    {
        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("Collection pattern")).toBeTruthy();
        expect(templateApi.list).toHaveBeenCalledWith({scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION});
        expect(fieldApi.listSchemas).toHaveBeenCalledWith({scopeKind: FieldScopeKind.ORGANIZATION});
        expect(fieldApi.listSchemas).not.toHaveBeenCalledWith({scopeKind: FieldScopeKind.PLATFORM});

        fireEvent.click(screen.getByRole("button", {name: "Open Collection pattern"}));
        expect(await screen.findByRole("button", {name: "Back to Templates"})).toBeTruthy();
        expect(templateApi.get).toHaveBeenCalledWith("template-1");

        fireEvent.click(screen.getByRole("button", {name: "Add section"}));
        fireEvent.change(within(dialog()).getByLabelText("Title"), {target: {value: "Collected data"}});
        fireEvent.click(within(dialog()).getByRole("button", {name: "Save"}));
        fireEvent.click(screen.getByRole("button", {name: "Save draft"}));

        await waitFor(() => expect(templateApi.replace).toHaveBeenCalledWith("template-1", expect.objectContaining({
            sections: [expect.objectContaining({sectionKey: "collected-data", title: "Collected data"})],
        }), "\"draft-version-1:2026-09-02T00:00:00Z\""));
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
        }, expect.any(String)));
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

    it("copies a read-only platform Template into the caller's own Templates", async () =>
    {
        templateApi.get.mockResolvedValue({
            ...publishedTemplate(),
            scopeKind: InformationRequestTemplateScopeKind.PLATFORM,
        });
        templateApi.clone.mockResolvedValue({
            ...draftTemplate(),
            id: "template-copy",
            displayName: "Personal copy",
            scopeKind: InformationRequestTemplateScopeKind.PERSONAL,
        });
        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("Collection pattern")).toBeTruthy();
        fireEvent.click(screen.getByRole("tab", {name: "Platform"}));
        fireEvent.click(await screen.findByRole("button", {name: "Open Collection pattern"}));
        fireEvent.click(await screen.findByRole("tab", {name: "Versions"}));
        fireEvent.click(await screen.findByRole("button", {name: "Copy as a new Template"}));
        fireEvent.change(within(dialog()).getByLabelText("Copy into"), {target: {value: InformationRequestTemplateScopeKind.PERSONAL}});
        fireEvent.change(within(dialog()).getByLabelText("Name"), {target: {value: "Personal copy"}});
        fireEvent.click(within(dialog()).getByRole("button", {name: "Copy"}));

        await waitFor(() => expect(templateApi.clone).toHaveBeenCalledWith("template-1", {
            sourceVersionNumber: 1,
            target: {
                namespace: "process",
                templateKey: "personal-copy",
                displayName: "Personal copy",
                description: "Collect one typed response",
                scopeKind: InformationRequestTemplateScopeKind.PERSONAL,
            },
        }, expect.any(String)));
    });

    it("lets a platform administrator create and author Platform Templates", async () => {
        authState.platformAdministrator = true;
        templateApi.list.mockImplementation(async (params: { scopeKind: InformationRequestTemplateScopeKind }) =>
            [{...templateSummary, scopeKind: params.scopeKind}]);
        templateApi.get.mockResolvedValue({
            ...draftTemplate(),
            scopeKind: InformationRequestTemplateScopeKind.PLATFORM,
        });
        templateApi.create.mockResolvedValue({
            ...draftTemplate(),
            scopeKind: InformationRequestTemplateScopeKind.PLATFORM,
        });
        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("Collection pattern")).toBeTruthy();
        fireEvent.click(screen.getByRole("tab", {name: "Platform"}));
        await waitFor(() => expect(templateApi.list).toHaveBeenLastCalledWith({
            scopeKind: InformationRequestTemplateScopeKind.PLATFORM,
        }));
        expect(fieldApi.listSchemas).toHaveBeenLastCalledWith({scopeKind: FieldScopeKind.PLATFORM});
        fireEvent.click(await screen.findByRole("button", {name: "New Template"}));
        fireEvent.change(within(dialog()).getByLabelText("Name"), {target: {value: "Shared starting point"}});
        fireEvent.click(within(dialog()).getByRole("button", {name: "Create"}));

        await waitFor(() => expect(templateApi.create).toHaveBeenCalledWith(expect.objectContaining({
            templateKey: "shared-starting-point",
            scopeKind: InformationRequestTemplateScopeKind.PLATFORM,
        }), expect.any(String)));
        expect(await screen.findByRole("button", {name: "Save draft"})).toBeTruthy();
        expect(screen.queryByText("Read only")).toBeNull();
    });

    it("names every control of a read-only Template and its copy dialog", async () =>
    {
        templateApi.get.mockResolvedValue({
            ...publishedTemplate(),
            scopeKind: InformationRequestTemplateScopeKind.PLATFORM,
        });
        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("Collection pattern")).toBeTruthy();
        fireEvent.click(screen.getByRole("tab", {name: "Platform"}));
        fireEvent.click(await screen.findByRole("button", {name: "Open Collection pattern"}));
        fireEvent.click(await screen.findByRole("tab", {name: "Versions"}));
        fireEvent.click(await screen.findByRole("button", {name: "Copy as a new Template"}));

        expect(unnamedControls(document.body)).toEqual([]);
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
        expect(fieldApi.listSchemas).not.toHaveBeenCalledWith({scopeKind: FieldScopeKind.PLATFORM});
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

    it("says that typed answers are not in the plan where Templates are authored", async () => {
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(informationRequestCapabilities({typedAnswersAvailable: false}));
        templateApi.list.mockResolvedValue([]);

        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("This plan does not include typed answers. A Template can still ask for them, but a request that does cannot be issued until the plan includes Business Fields and Schemas.")).toBeTruthy();
    });

    it("shows no plan notice and reads no Templates for a person without the feature", () =>
    {
        authState.currentSession = {activeOrganizationId: null} as CurrentSessionDto;
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(capabilitiesWithoutTheFeature());

        render(<InformationRequestTemplatesTab/>);

        expect(screen.queryByRole("status")).toBeNull();
        expect(screen.queryByText(/does not include creating Information Requests/)).toBeNull();
        expect(templateApi.list).not.toHaveBeenCalled();
        expect(screen.queryByRole("button", {name: "New Template"})).toBeNull();
    });

    it("keeps Templates readable but offers no authoring while the organization cannot create new work", async () =>
    {
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(informationRequestCapabilities({
            ownerType: SubscriptionOwnerType.ORGANIZATION,
            planCode: PlanCode.BUSINESS,
            newWorkAvailable: false,
            newWorkUnavailableReason: InformationRequestStandingReason.SUBSCRIPTION_PAST_DUE,
            personalTemplatesAvailable: false,
        }));

        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("Collection pattern")).toBeTruthy();
        expect(screen.getByRole("status").textContent).toMatch(/Payment for the subscription is overdue/);
        expect(screen.queryByRole("button", {name: "New Template"})).toBeNull();
        fireEvent.click(screen.getByRole("tab", {name: "My Templates"}));
        await waitFor(() => expect(templateApi.list).toHaveBeenLastCalledWith({
            scopeKind: InformationRequestTemplateScopeKind.PERSONAL,
        }));
        expect(screen.queryByRole("button", {name: "New Template"})).toBeNull();
    });

    it("says why the list could not be read and lists the Templates once Try again reads them", async () =>
    {
        templateApi.list
            .mockRejectedValueOnce({errorMessage: "Request failed"})
            .mockResolvedValueOnce([templateSummary]);
        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("Information Request Templates could not be loaded.")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(await screen.findByText("Collection pattern")).toBeTruthy();
        expect(screen.queryByText("Information Request Templates could not be loaded.")).toBeNull();
        expect(templateApi.list).toHaveBeenCalledTimes(2);
    });

    it("states a refused list without offering a Try again that cannot succeed", async () => {
        templateApi.list.mockRejectedValue({
            errorMessage: "This workspace's plan does not include Information Requests",
            reasonCode: "SUBSCRIPTION_FEATURE_UNAVAILABLE",
        });
        render(<InformationRequestTemplatesTab/>);

        expect(await screen.findByText("This workspace's plan does not include Information Requests")).toBeTruthy();
        expect(screen.queryByText("Information Request Templates could not be loaded.")).toBeNull();
        expect(screen.queryByRole("button", {name: "Try again"})).toBeNull();
    });

    it("keeps the list in view when a Template cannot be opened", async () => {
        templateApi.get.mockRejectedValueOnce({status: 500});
        render(<InformationRequestTemplatesTab/>);

        fireEvent.click(await screen.findByRole("button", {name: "Open Collection pattern"}));

        expect(await screen.findByText("The Template could not be opened.")).toBeTruthy();
        expect(screen.getByRole("button", {name: "Open Collection pattern"})).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Try again"})).toBeNull();
    });

    it("states a failed read of the Schemas in the editor instead of offering none, and reads them again on Try again", async () => {
        fieldApi.listSchemas.mockRejectedValueOnce({status: 500}).mockResolvedValueOnce([]);
        render(<InformationRequestTemplatesTab/>);

        fireEvent.click(await screen.findByRole("button", {name: "Open Collection pattern"}));

        expect(await screen.findByText("The Schemas this scope can use could not be loaded.")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        await waitFor(() => expect(screen.queryByText("The Schemas this scope can use could not be loaded.")).toBeNull());
        expect(fieldApi.listSchemas).toHaveBeenCalledTimes(2);
        expect(fieldApi.listSchemas).toHaveBeenLastCalledWith({scopeKind: FieldScopeKind.ORGANIZATION});
    });
});
