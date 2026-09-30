/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import * as blueprints from "../../../../services/blueprintService.ts";
import * as templates from "../../../../services/informationRequestTemplateService.ts";
import {
    BlueprintDefinitionSummaryDto,
    InformationRequestDto,
    InformationRequestOwnerType,
    InformationRequestShareRoleKey,
    InformationRequestState,
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    InformationRequestTemplateSummaryDto,
} from "../../../models/models.tsx";
import CreateInformationRequestDialog from "./CreateInformationRequestDialog.tsx";

vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({
    createInformationRequest: vi.fn(),
    getInformationRequestParties: vi.fn(),
    assignInformationRequestParty: vi.fn(),
}));
vi.mock("../../../../services/informationRequestTemplateService.ts", () => ({
    listInformationRequestTemplates: vi.fn(),
    getInformationRequestTemplate: vi.fn(),
}));
vi.mock("../../../../services/blueprintService.ts", () => ({listBlueprints: vi.fn()}));
vi.mock("../../../../services/fieldsService.ts", () => ({listSchemas: vi.fn().mockResolvedValue([])}));
vi.mock("../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({appUser: {id: "user-a"}, currentSession: {activeOrganizationId: "org-a"}}),
}));

const template = (overrides: Partial<InformationRequestTemplateSummaryDto>): InformationRequestTemplateSummaryDto => ({
    id: "template-a",
    scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION,
    namespace: "process",
    templateKey: "periodic-records",
    displayName: "Periodic records",
    status: InformationRequestTemplateStatus.PUBLISHED,
    hasEditableVersion: false,
    latestPublishedVersionNumber: 2,
    createdAt: "2026-09-01T08:00:00Z",
    updatedAt: "2026-09-02T08:00:00Z",
    ...overrides,
});

const created: InformationRequestDto = {
    id: "request-new",
    exchangeId: "exchange-a",
    templateVersionId: "version-a",
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    ownerOrganizationId: "org-a",
    state: InformationRequestState.DRAFT,
    gatesExchangeClosure: true,
    aggregateRevision: 1,
    createdAt: "2026-09-27T08:00:00Z",
    updatedAt: "2026-09-27T08:00:00Z",
    requestETag: "\"request-1\"",
    conditionEvaluations: [],
};

const blueprint = (overrides: Partial<BlueprintDefinitionSummaryDto>): BlueprintDefinitionSummaryDto => ({
    id: "blueprint-a",
    name: "Records blueprint",
    scope: "ORG",
    isActive: true,
    isPublished: true,
    isTemplate: false,
    generalTags: [],
    configJson: "{}",
    exchangeDocuments: [],
    participants: [],
    informationRequestTemplateVersionId: "version-b",
    createdAt: "2026-09-01T08:00:00Z",
    updatedAt: "2026-09-01T08:00:00Z",
    ...overrides,
});

const renderDialog = () =>
{
    const onCreated = vi.fn();
    const onDismiss = vi.fn();
    render(<CreateInformationRequestDialog open={true}
                                           exchangeId={"exchange-a"}
                                           onCreated={onCreated}
                                           onDismiss={onDismiss}/>);
    return {onCreated, onDismiss};
};

const choose = (label: string, option: string) =>
{
    const select = screen.getByLabelText(label) as HTMLSelectElement;
    const value = Array.from(select.options).find(candidate => candidate.textContent === option)?.value ?? "";
    fireEvent.change(select, {target: {value}});
};

describe("CreateInformationRequestDialog", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });

    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(templates.listInformationRequestTemplates).mockImplementation(async params =>
            params?.scopeKind === InformationRequestTemplateScopeKind.PLATFORM
                ? [template({id: "template-p", displayName: "Platform collection", scopeKind: InformationRequestTemplateScopeKind.PLATFORM})]
                : [template({}), template({id: "template-d", displayName: "Unpublished draft", latestPublishedVersionNumber: undefined})]);
        vi.mocked(templates.getInformationRequestTemplate).mockResolvedValue({
            id: "template-a",
            latestPublishedVersion: {id: "version-a"},
        } as InformationRequestTemplateDto);
        vi.mocked(blueprints.listBlueprints).mockImplementation(async params =>
            params?.scope === "APP"
                ? []
                : [blueprint({}), blueprint({id: "blueprint-x", name: "No request blueprint", informationRequestTemplateVersionId: undefined})]);
        vi.mocked(authoring.createInformationRequest).mockResolvedValue(created);
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({parties: [], partiesETag: "\"parties-1\""});
        vi.mocked(authoring.assignInformationRequestParty).mockResolvedValue({party: {} as never, partiesETag: "\"parties-2\""});
    });

    afterEach(cleanup);

    it("creates from a Template's published Version and names the author its Decision Maker", async () =>
    {
        const {onCreated} = renderDialog();

        await screen.findByRole("option", {name: "Periodic records"});
        expect(screen.queryByRole("option", {name: "Platform collection"})).toBeNull();
        expect(screen.getByText(/To start from a platform Template, copy it first/)).toBeTruthy();
        expect(screen.queryByRole("option", {name: "Unpublished draft"})).toBeNull();
        choose("Template", "Periodic records");
        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created));
        const [request, key] = vi.mocked(authoring.createInformationRequest).mock.calls[0];
        expect(request).toEqual({exchangeId: "exchange-a", templateVersionId: "version-a"});
        expect(key).toBeTruthy();
        expect(authoring.assignInformationRequestParty).toHaveBeenCalledWith(
            "request-new",
            {roleKey: InformationRequestShareRoleKey.DECISION_MAKER, userId: "user-a"},
            "\"parties-1\"",
            expect.any(String),
        );
    });

    it("creates from a Blueprint that names a Template Version and offers only such Blueprints", async () =>
    {
        const {onCreated} = renderDialog();

        fireEvent.click(await screen.findByRole("radio", {name: "From a Blueprint"}));
        await screen.findByRole("option", {name: "Records blueprint"});
        expect(screen.queryByRole("option", {name: "No request blueprint"})).toBeNull();
        choose("Blueprint", "Records blueprint");
        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        await waitFor(() => expect(onCreated).toHaveBeenCalled());
        expect(vi.mocked(authoring.createInformationRequest).mock.calls[0][0])
            .toEqual({exchangeId: "exchange-a", blueprintDefinitionId: "blueprint-a"});
    });

    it("writes a one-off request in the document editor and keeps Create unavailable until it can be sent", async () =>
    {
        renderDialog();

        fireEvent.click(await screen.findByRole("radio", {name: "Write a one-off request"}));
        expect(screen.getByText("Add at least one section.")).toBeTruthy();
        fireEvent.change(screen.getByLabelText("Request name"), {target: {value: "Quarterly figures"}});

        expect((screen.getByRole("button", {name: "Create"}) as HTMLButtonElement).disabled).toBe(true);
        expect(authoring.createInformationRequest).not.toHaveBeenCalled();
    });

    it("keeps the dialog open with a refusal and retries under the same Idempotency-Key", async () =>
    {
        vi.mocked(authoring.createInformationRequest)
            .mockRejectedValueOnce({errorMessage: "Information Requests cannot be created for a deleted Exchange"})
            .mockResolvedValueOnce(created);
        const {onCreated} = renderDialog();

        await screen.findByRole("option", {name: "Periodic records"});
        choose("Template", "Periodic records");
        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        expect(await screen.findByText("Information Requests cannot be created for a deleted Exchange")).toBeTruthy();
        expect(onCreated).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole("button", {name: "Create"}));
        await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created));
        const keys = vi.mocked(authoring.createInformationRequest).mock.calls.map(call => call[1]);
        expect(keys[0]).toBe(keys[1]);
    });

    it("still opens the new request when naming the Decision Maker fails", async () =>
    {
        vi.mocked(authoring.assignInformationRequestParty).mockRejectedValue({errorMessage: "Access denied"});
        const {onCreated} = renderDialog();

        await screen.findByRole("option", {name: "Periodic records"});
        choose("Template", "Periodic records");
        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created));
    });
});
