/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as templates from "../../../../services/informationRequestTemplateService.ts";
import {
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    InformationRequestTemplateSummaryDto,
} from "../../../models/models.tsx";
import BlueprintInformationRequestTab from "./BlueprintInformationRequestTab.tsx";
import {templateVersionChange} from "./blueprintTemplateVersionChange.ts";

vi.mock("../../../../services/informationRequestTemplateService.ts", () => ({
    listInformationRequestTemplates: vi.fn(),
    getInformationRequestTemplate: vi.fn(),
}));

const summary = (id: string, displayName: string, scopeKind: InformationRequestTemplateScopeKind): InformationRequestTemplateSummaryDto => ({
    id,
    scopeKind,
    namespace: "process",
    templateKey: id,
    displayName,
    status: InformationRequestTemplateStatus.PUBLISHED,
    hasEditableVersion: false,
    latestPublishedVersionNumber: 3,
    createdAt: "2026-09-01T08:00:00Z",
    updatedAt: "2026-09-01T08:00:00Z",
});

describe("BlueprintInformationRequestTab", () =>
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
                ? [summary("template-p", "Platform collection", InformationRequestTemplateScopeKind.PLATFORM)]
                : [summary("template-a", "Periodic records", InformationRequestTemplateScopeKind.ORGANIZATION)]);
        vi.mocked(templates.getInformationRequestTemplate).mockImplementation(async id => ({
            id,
            displayName: id === "template-a" ? "Periodic records" : "Platform collection",
            latestPublishedVersion: {id: `${id}-version-3`, versionNumber: 3},
        }) as InformationRequestTemplateDto);
    });

    afterEach(cleanup);

    it("pins the latest published Version of the Template the author chooses for the Blueprint's scope", async () =>
    {
        const onChange = vi.fn();
        render(<BlueprintInformationRequestTab scope={"ORG"}
                                               templateVersionId={undefined}
                                               onChange={onChange}/>);

        await screen.findByRole("option", {name: "Periodic records"});
        expect(screen.getByRole("option", {name: "Platform collection"})).toBeTruthy();
        const select = screen.getByLabelText("Template") as HTMLSelectElement;
        fireEvent.change(select, {target: {value: "template-a"}});

        await waitFor(() => expect(onChange).toHaveBeenCalledWith("template-a-version-3"));
        expect(templates.listInformationRequestTemplates).toHaveBeenCalledWith({scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION});
    });

    it("names the pinned Version and removes it on request", async () =>
    {
        const onChange = vi.fn();
        render(<BlueprintInformationRequestTab scope={"PERSONAL"}
                                               templateVersionId={"template-a-version-3"}
                                               onChange={onChange}/>);

        expect(await screen.findByText("Exchanges from this Blueprint start an Information Request from Periodic records, version 3.")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Remove the Information Request"}));

        expect(onChange).toHaveBeenCalledWith(undefined);
        expect(templates.listInformationRequestTemplates).toHaveBeenCalledWith({scopeKind: InformationRequestTemplateScopeKind.PERSONAL});
    });

    it("states only what changed about the pinned Version when saving", () =>
    {
        expect(templateVersionChange(undefined, "version-a")).toEqual({informationRequestTemplateVersionId: "version-a"});
        expect(templateVersionChange("version-a", "version-a")).toEqual({});
        expect(templateVersionChange("version-a", "version-b")).toEqual({informationRequestTemplateVersionId: "version-b"});
        expect(templateVersionChange("version-a", undefined)).toEqual({clearInformationRequestTemplateVersion: true});
        expect(templateVersionChange(undefined, undefined)).toEqual({});
    });
});
