/** @vitest-environment jsdom */
import {act, cleanup, renderHook, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import * as blueprints from "../../../../services/blueprintService.ts";
import * as templates from "../../../../services/informationRequestTemplateService.ts";
import {
    InformationRequestOwnerType,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    InformationRequestTemplateSummaryDto,
} from "../../../models/models.tsx";
import {InformationRequestOwnerFields} from "../../shared/ownerWorkspace.ts";
import {useRequestSourceOptions} from "./useRequestSourceOptions.ts";

const session = vi.hoisted(() => ({current: null as { userId: string; activeOrganizationId: string | null } | null}));

vi.mock("../../../../context/AuthContext.tsx", () => ({useAuth: () => ({currentSession: session.current})}));
vi.mock("../../../../services/informationRequestTemplateService.ts", () => ({listInformationRequestTemplates: vi.fn()}));
vi.mock("../../../../services/blueprintService.ts", () => ({listBlueprints: vi.fn().mockResolvedValue([])}));
vi.mock("../../../../services/fieldsService.ts", () => ({listSchemas: vi.fn().mockResolvedValue([])}));

const owner = (organizationId: string): InformationRequestOwnerFields =>
    ({ownerType: InformationRequestOwnerType.ORGANIZATION, ownerOrganizationId: organizationId});

const working = (organizationId: string | null) => {
    session.current = {userId: "user-a", activeOrganizationId: organizationId};
};

const template = (organizationId: string): InformationRequestTemplateSummaryDto => ({
    id: `${organizationId}-template`,
    scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION,
    namespace: "records",
    templateKey: "record-check",
    displayName: `${organizationId} records`,
    status: InformationRequestTemplateStatus.PUBLISHED,
    hasEditableVersion: false,
    latestPublishedVersionNumber: 1,
    createdAt: 0,
    updatedAt: 0,
});

const renderOptions = (exchangeOwner: InformationRequestOwnerFields) =>
    renderHook(({current}) => useRequestSourceOptions(current), {initialProps: {current: exchangeOwner}});

describe("useRequestSourceOptions", () => {
    beforeEach(() => {
        vi.mocked(templates.listInformationRequestTemplates).mockReset();
        vi.mocked(templates.listInformationRequestTemplates)
            .mockImplementation(async () => [template(session.current?.activeOrganizationId ?? "personal")]);
        working("organization-a");
    });

    afterEach(cleanup);

    it("lists the sources of the workspace that owns the Exchange", async () => {
        const {result} = renderOptions(owner("organization-a"));

        await waitFor(() => expect(result.current.loaded).toBe(true));
        expect(result.current.templates.map(item => item.id)).toEqual(["organization-a-template"]);
        expect(result.current.otherWorkspace).toBe(false);
    });

    it("drops the earlier workspace's sources at once and lists nothing of the next when the session leaves the owner", async () => {
        const {result, rerender} = renderOptions(owner("organization-a"));
        await waitFor(() => expect(result.current.templates).toHaveLength(1));

        working("organization-b");
        rerender({current: owner("organization-a")});

        expect(result.current.templates).toEqual([]);
        expect(result.current.otherWorkspace).toBe(true);
        await act(async () => Promise.resolve());
        expect(result.current.templates).toEqual([]);
        expect(templates.listInformationRequestTemplates).toHaveBeenCalledTimes(1);
    });

    it("lists the owner's sources once the session reaches the owner's workspace", async () => {
        working("organization-b");
        const {result, rerender} = renderOptions(owner("organization-a"));
        expect(result.current.otherWorkspace).toBe(true);
        expect(templates.listInformationRequestTemplates).not.toHaveBeenCalled();

        working("organization-a");
        rerender({current: owner("organization-a")});

        await waitFor(() => expect(result.current.templates.map(item => item.id)).toEqual(["organization-a-template"]));
        expect(result.current.otherWorkspace).toBe(false);
    });

    it("never shows a late answer for a workspace the session already left", async () => {
        let answer: (listed: InformationRequestTemplateSummaryDto[]) => void = () => undefined;
        vi.mocked(templates.listInformationRequestTemplates).mockImplementation(() => new Promise(resolve => {
            answer = resolve;
        }));
        const {result, rerender} = renderOptions(owner("organization-a"));

        session.current = null;
        rerender({current: owner("organization-a")});
        await act(async () => answer([template("organization-a")]));

        expect(result.current.templates).toEqual([]);
        expect(result.current.loaded).toBe(false);
        expect(result.current.otherWorkspace).toBe(false);
    });

    it("keeps the Templates and says the Blueprints could not be listed when their listing is refused", async () => {
        vi.mocked(blueprints.listBlueprints).mockRejectedValueOnce({errorMessage: "Blueprints are unavailable"});
        const {result} = renderOptions(owner("organization-a"));

        await waitFor(() => expect(result.current.loaded).toBe(true));
        expect(result.current.templates.map(item => item.id)).toEqual(["organization-a-template"]);
        expect(result.current.blueprints).toEqual([]);
        expect(result.current.blueprintsUnavailable).toBe(true);
        expect(result.current.error).toBeNull();
    });

    it("reports a refused read and offers no source", async () => {
        vi.mocked(templates.listInformationRequestTemplates).mockRejectedValue({errorMessage: "Sources are unavailable"});
        const {result} = renderOptions(owner("organization-a"));

        await waitFor(() => expect(result.current.error).toBe("Sources are unavailable"));
        expect(result.current.templates).toEqual([]);
        expect(result.current.loaded).toBe(false);
    });
});
