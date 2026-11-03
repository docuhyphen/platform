/** @vitest-environment jsdom */
import {act, renderHook, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";
import {
    Capability,
    CurrentSessionDto,
    InformationRequestStandingReason,
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    InformationRequestTemplateSummaryDto,
    SubscriptionOwnerType,
} from "../../models/models.tsx";
import {useInformationRequestTemplateAdministration} from "./useInformationRequestTemplateAdministration.ts";
import {listSchemas} from "../../../services/fieldsService.ts";
import {informationRequestCapabilities} from "../../information-requests/shared/testing/capabilityFixtures.ts";

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

const authState = vi.hoisted(() => ({
    capabilities: new Set<string>(),
    currentSession: {activeOrganizationId: null} as CurrentSessionDto,
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
    listSchemas: vi.fn(async () => []),
}));

vi.mock("../../../context/AuthContext.tsx", () => ({
    useAuth: () => ({
        currentSession: authState.currentSession,
        hasCapability: (capability: Capability) => authState.capabilities.has(capability),
    }),
}));

const summary: InformationRequestTemplateSummaryDto = {
    id: "template-1",
    scopeKind: InformationRequestTemplateScopeKind.PERSONAL,
    namespace: "process",
    templateKey: "collection-pattern",
    displayName: "Collection pattern",
    status: InformationRequestTemplateStatus.DRAFT,
    hasEditableVersion: true,
    createdAt: Date.parse("2026-09-02T00:00:00Z"),
    updatedAt: Date.parse("2026-09-02T00:00:00Z"),
};

const withDraft = (revision: number): InformationRequestTemplateDto => ({
    ...summary,
    updatedAt: Date.parse(`2026-09-0${revision + 2}T00:00:00Z`),
    draftETag: `"draft-version-1:${revision}"`,
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

describe("useInformationRequestTemplateAdministration", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authState.capabilities = new Set();
        authState.currentSession = {activeOrganizationId: null} as CurrentSessionDto;
        templateApi.list.mockResolvedValue([summary]);
        templateApi.get.mockResolvedValue(withDraft(0));
    });

    const opened = async () => {
        const hook = renderHook(() => useInformationRequestTemplateAdministration(null));
        await waitFor(() => expect(hook.result.current.templates).toEqual([summary]));
        await act(async () => hook.result.current.open(summary));
        return hook;
    };

    it("saves against the draft tag it read and publishes against the tag the save returned", async () => {
        templateApi.replace.mockResolvedValue(withDraft(1));
        templateApi.publish.mockResolvedValue({...withDraft(1), draftVersion: undefined, draftETag: undefined});
        const hook = await opened();

        await act(async () => hook.result.current.commands?.save({sections: []}));
        await act(async () => hook.result.current.commands?.publish());

        expect(templateApi.replace).toHaveBeenCalledWith("template-1", {sections: []}, "\"draft-version-1:0\"");
        expect(templateApi.publish).toHaveBeenCalledWith("template-1", "\"draft-version-1:1\"", expect.any(String));
    });

    it("retries a lifecycle command under the key of its first attempt and uses a fresh key afterwards", async () => {
        templateApi.retire
            .mockRejectedValueOnce("Network Error")
            .mockResolvedValue(withDraft(0));
        const hook = await opened();

        await act(async () => hook.result.current.commands?.retire(2).catch((): null => null));
        await act(async () => hook.result.current.commands?.retire(2));
        await act(async () => hook.result.current.commands?.retire(2));

        const keys = templateApi.retire.mock.calls.map(call => call[2]);
        expect(templateApi.retire.mock.calls.map(call => call.slice(0, 2))).toEqual([
            ["template-1", 2], ["template-1", 2], ["template-1", 2],
        ]);
        expect(keys[0]).toBe(keys[1]);
        expect(keys[2]).not.toBe(keys[1]);
    });

    it("sends an idempotency key with every create, new version, and copy", async () => {
        templateApi.create.mockResolvedValue(withDraft(0));
        templateApi.newVersion.mockResolvedValue(withDraft(0));
        templateApi.clone.mockResolvedValue(withDraft(0));
        const hook = await opened();
        const target = {
            namespace: "process",
            templateKey: "copy",
            displayName: "Copy",
            scopeKind: InformationRequestTemplateScopeKind.PERSONAL,
        };

        await act(async () => void await hook.result.current.create(target));
        await act(async () => hook.result.current.commands?.startDraft(1));
        await act(async () => hook.result.current.commands?.clone(1, target));

        expect(templateApi.create).toHaveBeenCalledWith(target, expect.any(String));
        expect(templateApi.newVersion).toHaveBeenCalledWith("template-1", {sourceVersionNumber: 1}, expect.any(String));
        expect(templateApi.clone).toHaveBeenCalledWith("template-1", {
            sourceVersionNumber: 1,
            target
        }, expect.any(String));
    });

    it("lets a platform administrator manage and copy into Platform Templates", async () => {
        authState.capabilities = new Set([Capability.APP_ADMIN]);
        const hook = renderHook(() => useInformationRequestTemplateAdministration(null));

        act(() => hook.result.current.setScope(InformationRequestTemplateScopeKind.PLATFORM));

        await waitFor(() => expect(templateApi.list).toHaveBeenLastCalledWith({
            scopeKind: InformationRequestTemplateScopeKind.PLATFORM,
        }));
        expect(hook.result.current.canManageScope).toBe(true);
        expect(hook.result.current.copyTargets).toContain(InformationRequestTemplateScopeKind.PLATFORM);
    });

    it("keeps organization Templates editable while the organization is at its open-request limit", async () => {
        authState.currentSession = {activeOrganizationId: "organization-a"} as CurrentSessionDto;
        authState.capabilities = new Set([Capability.ORG_POLICY_MANAGE]);
        const atLimit = informationRequestCapabilities({
            ownerType: SubscriptionOwnerType.ORGANIZATION,
            newWorkAvailable: false,
            newWorkUnavailableReason: InformationRequestStandingReason.OPEN_REQUEST_LIMIT_REACHED,
            quotas: {maxOpenRequests: 25, openRequests: 25, committedEvidenceBytes: 0},
        });
        const hook = renderHook(() => useInformationRequestTemplateAdministration(atLimit));

        await waitFor(() => expect(templateApi.list).toHaveBeenCalled());
        expect(hook.result.current.canManageScope).toBe(true);
        expect(hook.result.current.copyTargets).toContain(InformationRequestTemplateScopeKind.ORGANIZATION);
    });

    it("reads no Schemas for a scope whose Templates the caller only reads", async () => {
        const hook = renderHook(() => useInformationRequestTemplateAdministration(null));
        await waitFor(() => expect(listSchemas).toHaveBeenCalledTimes(1));
        vi.mocked(listSchemas).mockClear();

        act(() => hook.result.current.setScope(InformationRequestTemplateScopeKind.PLATFORM));

        await waitFor(() => expect(templateApi.list).toHaveBeenLastCalledWith({
            scopeKind: InformationRequestTemplateScopeKind.PLATFORM,
        }));
        expect(listSchemas).not.toHaveBeenCalled();
        expect(hook.result.current.schemasError).toBeNull();
    });

    it("states a refused Template list as such instead of offering a retry", async () => {
        templateApi.list.mockRejectedValue({
            errorMessage: "This workspace's plan does not include Information Requests",
            reasonCode: "SUBSCRIPTION_FEATURE_UNAVAILABLE",
        });
        const hook = renderHook(() => useInformationRequestTemplateAdministration(null));

        await waitFor(() => expect(hook.result.current.listRefusal)
            .toBe("This workspace's plan does not include Information Requests"));
        expect(hook.result.current.listError).toBeNull();
    });

    it("keeps the list of the scope now chosen when an earlier scope's list answers late", async () => {
        authState.capabilities = new Set([Capability.APP_ADMIN]);
        let answerPersonal: (templates: InformationRequestTemplateSummaryDto[]) => void = () => undefined;
        const platform = {...summary, id: "platform-template", scopeKind: InformationRequestTemplateScopeKind.PLATFORM};
        templateApi.list.mockImplementation(({scopeKind}: { scopeKind: InformationRequestTemplateScopeKind }) =>
            scopeKind === InformationRequestTemplateScopeKind.PERSONAL
                ? new Promise<InformationRequestTemplateSummaryDto[]>(resolve => {
                    answerPersonal = resolve;
                })
                : Promise.resolve([platform]));
        const hook = renderHook(() => useInformationRequestTemplateAdministration(null));

        act(() => hook.result.current.setScope(InformationRequestTemplateScopeKind.PLATFORM));
        await waitFor(() => expect(hook.result.current.templates).toEqual([platform]));
        await act(async () => answerPersonal([summary]));

        expect(hook.result.current.templates).toEqual([platform]);
    });

    it("keeps Platform Templates read-only for everyone else", async () => {
        const hook = renderHook(() => useInformationRequestTemplateAdministration(null));

        act(() => hook.result.current.setScope(InformationRequestTemplateScopeKind.PLATFORM));

        await waitFor(() => expect(templateApi.list).toHaveBeenLastCalledWith({
            scopeKind: InformationRequestTemplateScopeKind.PLATFORM,
        }));
        expect(hook.result.current.canManageScope).toBe(false);
        expect(hook.result.current.copyTargets).not.toContain(InformationRequestTemplateScopeKind.PLATFORM);
    });
});
