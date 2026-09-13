import {beforeEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestRequirementType,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
} from "../../app/models/models.tsx";

const get = vi.fn();
const post = vi.fn();
const put = vi.fn();
const patch = vi.fn();

vi.mock("../apiClient.ts", () => ({
    default: {
        get: (...args: unknown[]) => get(...args),
        post: (...args: unknown[]) => post(...args),
        put: (...args: unknown[]) => put(...args),
        patch: (...args: unknown[]) => patch(...args),
    },
}));

const template = {
    id: "template-1",
    scopeKind: InformationRequestTemplateScopeKind.PERSONAL,
    scopeUserId: "user-1",
    namespace: "process",
    templateKey: "collection-pattern",
    displayName: "Collection pattern",
    status: InformationRequestTemplateStatus.DRAFT,
    createdAt: "2026-09-01T00:00:00Z",
    updatedAt: "2026-09-01T00:00:00Z",
};

const createRequest = {
    namespace: "process",
    templateKey: "collection-pattern",
    displayName: "Collection pattern",
    scopeKind: InformationRequestTemplateScopeKind.PERSONAL,
};

const configuration = {
    sections: [
        {
            sectionKey: "collected-data",
            title: "Collected data",
            requirements: [
                {
                    requirementKey: "recorded-note",
                    requirementType: InformationRequestRequirementType.FIELD,
                    prompt: "State the recorded note",
                },
            ],
        },
    ],
};

const refusal = {
    isAxiosError: true,
    message: "Request failed",
    response: {data: {errorMessage: "Denied"}},
};

describe("informationRequestTemplateService", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("lists templates in the requested owner scope", async () =>
    {
        get.mockResolvedValueOnce({data: [template]});
        const {listInformationRequestTemplates} = await import("../informationRequestTemplateService.ts");

        const result = await listInformationRequestTemplates({
            scopeKind: InformationRequestTemplateScopeKind.PERSONAL,
        });

        expect(get).toHaveBeenCalledWith(
            "/information-request-templates",
            {params: {scopeKind: InformationRequestTemplateScopeKind.PERSONAL}},
        );
        expect(result).toEqual([template]);
    });

    it("creates, reads, configures, and publishes a template", async () =>
    {
        post.mockResolvedValue({data: template});
        get.mockResolvedValueOnce({data: template});
        put.mockResolvedValueOnce({data: template});
        const {
            createInformationRequestTemplate,
            getInformationRequestTemplate,
            replaceInformationRequestTemplateDraftConfiguration,
            publishInformationRequestTemplateDraft,
        } = await import("../informationRequestTemplateService.ts");

        await createInformationRequestTemplate(createRequest);
        await getInformationRequestTemplate("template-1");
        await replaceInformationRequestTemplateDraftConfiguration("template-1", configuration);
        await publishInformationRequestTemplateDraft("template-1");

        expect(post).toHaveBeenCalledWith("/information-request-templates", createRequest);
        expect(get).toHaveBeenCalledWith("/information-request-templates/template-1");
        expect(put).toHaveBeenCalledWith(
            "/information-request-templates/template-1/draft/configuration",
            configuration,
        );
        expect(post).toHaveBeenCalledWith(
            "/information-request-templates/template-1/draft/publication",
            {},
        );
    });

    it("uses exact source versions for new versions, retirement, and clone", async () =>
    {
        post.mockResolvedValue({data: template});
        const {
            createInformationRequestTemplateDraftVersion,
            retireInformationRequestTemplateVersion,
            cloneInformationRequestTemplate,
        } = await import("../informationRequestTemplateService.ts");

        await createInformationRequestTemplateDraftVersion("template-1", {sourceVersionNumber: 2});
        await retireInformationRequestTemplateVersion("template-1", 2);
        await cloneInformationRequestTemplate("template-1", {
            sourceVersionNumber: 2,
            target: createRequest,
        });

        expect(post).toHaveBeenCalledWith(
            "/information-request-templates/template-1/versions",
            {sourceVersionNumber: 2},
        );
        expect(post).toHaveBeenCalledWith(
            "/information-request-templates/template-1/versions/2/retirement",
            {},
        );
        expect(patch).not.toHaveBeenCalled();
        expect(post).toHaveBeenCalledWith(
            "/information-request-templates/template-1/clones",
            {sourceVersionNumber: 2, target: createRequest},
        );
    });

    it("throws a refusal as the server stated it", async () =>
    {
        get.mockRejectedValueOnce(refusal);
        const {getInformationRequestTemplate} = await import("../informationRequestTemplateService.ts");

        await expect(getInformationRequestTemplate("template-1")).rejects.toEqual({errorMessage: "Denied"});
    });
});
