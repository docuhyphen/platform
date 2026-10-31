/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import * as blueprints from "../../../../services/blueprintService.ts";
import * as fields from "../../../../services/fieldsService.ts";
import * as templates from "../../../../services/informationRequestTemplateService.ts";
import {
    BlueprintDefinitionSummaryDto,
    FieldLifecycleStatus,
    FieldScopeKind,
    InformationRequestDto,
    InformationRequestExecutionStandingKind,
    InformationRequestOwnerType,
    InformationRequestShareRoleKey,
    InformationRequestState,
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    InformationRequestTemplateSummaryDto,
    SchemaDefinitionDto,
} from "../../../models/models.tsx";
import {InformationRequestOwnerFields} from "../../shared/ownerWorkspace.ts";
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
const session = vi.hoisted(() => ({current: {userId: "user-a", activeOrganizationId: "org-a" as string | null}}));

vi.mock("../../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({appUser: {id: "user-a"}, currentSession: session.current}),
}));

const OWNER: InformationRequestOwnerFields = {
    ownerType: InformationRequestOwnerType.ORGANIZATION,
    ownerOrganizationId: "org-a"
};

const template = (overrides: Partial<InformationRequestTemplateSummaryDto>): InformationRequestTemplateSummaryDto => ({
    id: "template-a",
    scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION,
    namespace: "process",
    templateKey: "periodic-records",
    displayName: "Periodic records",
    status: InformationRequestTemplateStatus.PUBLISHED,
    hasEditableVersion: false,
    latestPublishedVersionNumber: 2,
    createdAt: Date.parse("2026-09-01T08:00:00Z"),
    updatedAt: Date.parse("2026-09-02T08:00:00Z"),
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
    createdAt: Date.parse("2026-09-27T08:00:00Z"),
    updatedAt: Date.parse("2026-09-27T08:00:00Z"),
    requestETag: "\"request-1\"",
    executionStanding: {kind: InformationRequestExecutionStandingKind.ACTIVE},
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
    createdAt: Date.parse("2026-09-01T08:00:00Z"),
    updatedAt: Date.parse("2026-09-01T08:00:00Z"),
    ...overrides,
});

const platformSchema: SchemaDefinitionDto = {
    id: "schema-p",
    scopeKind: FieldScopeKind.PLATFORM,
    namespace: "platform",
    schemaKey: "shared-questions",
    displayName: "Shared questions",
    targetResourceType: "INFORMATION_REQUEST",
    status: FieldLifecycleStatus.PUBLISHED,
    latestPublishedVersion: {
        id: "schema-version-p",
        schemaDefinitionId: "schema-p",
        versionNumber: 3,
        status: FieldLifecycleStatus.PUBLISHED,
        bindings: [],
        createdAt: Date.parse("2026-09-01T08:00:00Z"),
    },
    createdAt: Date.parse("2026-09-01T08:00:00Z"),
};

const renderDialog = (owner = OWNER) =>
{
    const onCreated = vi.fn();
    const onDismiss = vi.fn();
    render(<CreateInformationRequestDialog open={true}
                                           exchangeId={"exchange-a"}
                                           owner={owner}
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
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    beforeEach(() =>
    {
        vi.clearAllMocks();
        session.current = {userId: "user-a", activeOrganizationId: "org-a"};
        vi.mocked(templates.listInformationRequestTemplates).mockImplementation(async params =>
            params?.scopeKind === InformationRequestTemplateScopeKind.PLATFORM
                ? [template({
                    id: "template-p",
                    displayName: "Platform collection",
                    scopeKind: InformationRequestTemplateScopeKind.PLATFORM
                })]
                : [template({}), template({
                    id: "template-d",
                    displayName: "Unpublished draft",
                    latestPublishedVersionNumber: undefined
                })]);
        vi.mocked(fields.listSchemas).mockResolvedValue([]);
        vi.mocked(templates.getInformationRequestTemplate).mockResolvedValue({
            id: "template-a",
            latestPublishedVersion: {id: "version-a"},
        } as InformationRequestTemplateDto);
        vi.mocked(blueprints.listBlueprints).mockImplementation(async params =>
            params?.scope === "APP"
                ? []
                : [blueprint({}), blueprint({
                    id: "blueprint-x",
                    name: "No request blueprint",
                    informationRequestTemplateVersionId: undefined
                })]);
        vi.mocked(authoring.createInformationRequest).mockReset().mockResolvedValue(created);
        vi.mocked(authoring.getInformationRequestParties).mockResolvedValue({
            parties: [],
            partiesETag: "\"parties-1\""
        });
        vi.mocked(authoring.assignInformationRequestParty).mockResolvedValue({
            party: {} as never,
            partiesETag: "\"parties-2\""
        });
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

        await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created, null));
        const [request, key] = vi.mocked(authoring.createInformationRequest).mock.calls[0];
        expect(request).toEqual({exchangeId: "exchange-a", templateVersionId: "version-a", gatesExchangeClosure: true});
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
            .toEqual({exchangeId: "exchange-a", blueprintDefinitionId: "blueprint-a", gatesExchangeClosure: true});
    });

    it("asks whether the Exchange must wait for the request, yes by default, and sends the answer", async () => {
        const {onCreated} = renderDialog();

        await screen.findByRole("option", {name: "Periodic records"});
        const gate = screen.getByRole("checkbox", {name: "Must finish before the Exchange can end"}) as HTMLInputElement;
        expect(gate.checked).toBe(true);
        choose("Template", "Periodic records");
        fireEvent.click(gate);
        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created, null));
        expect(vi.mocked(authoring.createInformationRequest).mock.calls[0][0])
            .toEqual({exchangeId: "exchange-a", templateVersionId: "version-a", gatesExchangeClosure: false});
    });

    it("sends a changed answer as a new command with its own Idempotency-Key", async () => {
        vi.mocked(authoring.createInformationRequest)
            .mockRejectedValueOnce({errorMessage: "The Exchange is being changed"})
            .mockResolvedValueOnce(created);
        renderDialog();

        await screen.findByRole("option", {name: "Periodic records"});
        choose("Template", "Periodic records");
        fireEvent.click(screen.getByRole("button", {name: "Create"}));
        expect(await screen.findByText("The Exchange is being changed")).toBeTruthy();
        fireEvent.click(screen.getByRole("checkbox", {name: "Must finish before the Exchange can end"}));
        fireEvent.click(screen.getByRole("button", {name: "Create"}));

        await waitFor(() => expect(authoring.createInformationRequest).toHaveBeenCalledTimes(2));
        const [first, second] = vi.mocked(authoring.createInformationRequest).mock.calls;
        expect(first[0].gatesExchangeClosure).toBe(true);
        expect(second[0].gatesExchangeClosure).toBe(false);
        expect(first[1]).not.toBe(second[1]);
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
        await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created, null));
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

        await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created, "Access denied"));
    });

    it("states a refused read of the Templates to start from with Try again and offers them once a retry loads", async () => {
        const listed = vi.mocked(templates.listInformationRequestTemplates).getMockImplementation();
        vi.mocked(templates.listInformationRequestTemplates)
            .mockRejectedValueOnce({errorMessage: "The Templates could not be read."});
        if (listed) vi.mocked(templates.listInformationRequestTemplates).mockImplementation(listed);
        renderDialog();

        await waitFor(() => expect(document.getElementById("information-request-create-sources-load-error")?.textContent)
            .toContain("The Templates could not be read."));
        expect(screen.queryByRole("option", {name: "Periodic records"})).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        expect(await screen.findByRole("option", {name: "Periodic records"})).toBeTruthy();
        expect(document.getElementById("information-request-create-sources-load-error")).toBeNull();
    });

    it("keeps offering Templates when the Blueprints cannot be listed and says so under the Blueprint choice", async () => {
        const listed = vi.mocked(blueprints.listBlueprints).getMockImplementation();
        vi.mocked(blueprints.listBlueprints).mockRejectedValueOnce({errorMessage: "Blueprints are unavailable"});
        if (listed) vi.mocked(blueprints.listBlueprints).mockImplementation(listed);
        renderDialog();

        expect(await screen.findByRole("option", {name: "Periodic records"})).toBeTruthy();
        expect(document.getElementById("information-request-create-sources-load-error")).toBeNull();
        fireEvent.click(screen.getByRole("radio", {name: "From a Blueprint"}));
        expect(document.getElementById("information-request-create-blueprints-load-error")?.textContent)
            .toContain("The Blueprints to start from could not be listed.");
        expect(screen.queryByText("No Blueprint names a Template Version yet.")).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));
        expect(await screen.findByRole("option", {name: "Records blueprint"})).toBeTruthy();
        expect(document.getElementById("information-request-create-blueprints-load-error")).toBeNull();
    });

    it("loads its sources for an author who may not list platform Schemas and offers the platform Schema the owner list carries", async () => {
        vi.mocked(fields.listSchemas).mockImplementation(async params => {
            if (params?.scopeKind === FieldScopeKind.PLATFORM)
                throw {errorMessage: "Platform schema configuration requires App Administrator access"};
            return [platformSchema];
        });
        renderDialog();

        expect(await screen.findByRole("option", {name: "Periodic records"})).toBeTruthy();
        expect(document.getElementById("information-request-create-sources-load-error")).toBeNull();
        expect(fields.listSchemas).toHaveBeenCalledWith({scopeKind: FieldScopeKind.ORGANIZATION});
        expect(fields.listSchemas).not.toHaveBeenCalledWith({scopeKind: FieldScopeKind.PLATFORM});
        fireEvent.click(screen.getByRole("radio", {name: "Write a one-off request"}));
        fireEvent.click(screen.getByRole("tab", {name: "Settings"}));
        expect(screen.getByRole("option", {name: "Shared questions (version 3)"})).toBeTruthy();
    });

    it("lists nothing of another workspace for an Exchange it does not own and still offers a one-off request", async () => {
        renderDialog({ownerType: InformationRequestOwnerType.ORGANIZATION, ownerOrganizationId: "org-b"});

        expect(await screen.findByText(/This Exchange belongs to another workspace/)).toBeTruthy();
        expect(screen.queryByLabelText("Template")).toBeNull();
        expect(document.getElementById("create-information-request-loading")).toBeNull();
        fireEvent.click(screen.getByRole("radio", {name: "From a Blueprint"}));
        expect(screen.queryByLabelText("Blueprint")).toBeNull();
        fireEvent.click(screen.getByRole("radio", {name: "Write a one-off request"}));
        expect(document.getElementById("create-information-request-dialog")).not.toBeNull();
        expect(templates.listInformationRequestTemplates).not.toHaveBeenCalled();
        expect(blueprints.listBlueprints).not.toHaveBeenCalled();
        expect(fields.listSchemas).not.toHaveBeenCalled();
    });

    it("lists the personal workspace's sources for a personally owned Exchange while working personally", async () => {
        session.current = {userId: "user-a", activeOrganizationId: null};
        renderDialog({ownerType: InformationRequestOwnerType.USER, ownerUserId: "user-a"});

        expect(await screen.findByRole("option", {name: "Periodic records"})).toBeTruthy();
        expect(templates.listInformationRequestTemplates).toHaveBeenCalledWith({scopeKind: InformationRequestTemplateScopeKind.PERSONAL});
        expect(blueprints.listBlueprints).toHaveBeenCalledWith({scope: "PERSONAL"});
    });
});

