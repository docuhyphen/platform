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

const summary = (
    id: string,
    displayName: string,
    scopeKind: InformationRequestTemplateScopeKind,
    status = InformationRequestTemplateStatus.PUBLISHED,
): InformationRequestTemplateSummaryDto => ({
    id,
    scopeKind,
    namespace: "process",
    templateKey: id,
    displayName,
    status,
    hasEditableVersion: false,
    latestPublishedVersionNumber: 3,
    latestPublishedVersionId: `${id}-version-3`,
    createdAt: Date.parse("2026-09-01T08:00:00Z"),
    updatedAt: Date.parse("2026-09-01T08:00:00Z"),
});

const versionStatus: Record<string, InformationRequestTemplateStatus> = {
    "template-v": InformationRequestTemplateStatus.RETIRED,
};

const names: Record<string, string> = {
    "template-a": "Periodic records",
    "template-p": "Platform collection",
    "template-r": "Retired collection",
    "template-v": "Withdrawn version",
};

describe("BlueprintInformationRequestTab", () =>
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
        vi.mocked(templates.listInformationRequestTemplates).mockImplementation(async params =>
            params?.scopeKind === InformationRequestTemplateScopeKind.PLATFORM
                ? [summary("template-p", "Platform collection", InformationRequestTemplateScopeKind.PLATFORM)]
                : [summary("template-a", "Periodic records", InformationRequestTemplateScopeKind.ORGANIZATION)]);
        vi.mocked(templates.getInformationRequestTemplate).mockImplementation(async id => ({
            id,
            displayName: names[id],
            latestPublishedVersion: {
                id: `${id}-version-3`,
                versionNumber: 3,
                status: versionStatus[id] ?? InformationRequestTemplateStatus.PUBLISHED,
            },
        }) as InformationRequestTemplateDto);
    });

    afterEach(cleanup);

    it("pins the latest published Version of a Template the Blueprint's own workspace holds", async () =>
    {
        const onChange = vi.fn();
        render(<BlueprintInformationRequestTab scope={"ORG"}
                                               templateVersionId={undefined}
                                               onChange={onChange}/>);

        await screen.findByRole("option", {name: "Periodic records"});
        expect(screen.queryByRole("option", {name: "Platform collection"})).toBeNull();
        const select = screen.getByLabelText("Template") as HTMLSelectElement;
        fireEvent.change(select, {target: {value: "template-a"}});

        await waitFor(() => expect(onChange).toHaveBeenCalledWith("template-a-version-3"));
        expect(templates.getInformationRequestTemplate).not.toHaveBeenCalled();
        expect(templates.listInformationRequestTemplates).toHaveBeenCalledWith({scopeKind: InformationRequestTemplateScopeKind.ORGANIZATION});
        expect(templates.listInformationRequestTemplates).not.toHaveBeenCalledWith({scopeKind: InformationRequestTemplateScopeKind.PLATFORM});
    });

    it("offers no retired Template and no Template whose latest Version was retired", async () => {
        vi.mocked(templates.listInformationRequestTemplates).mockResolvedValue([
            summary("template-a", "Periodic records", InformationRequestTemplateScopeKind.PERSONAL),
            summary("template-r", "Retired collection", InformationRequestTemplateScopeKind.PERSONAL, InformationRequestTemplateStatus.RETIRED),
            {
                ...summary("template-v", "Withdrawn version", InformationRequestTemplateScopeKind.PERSONAL),
                latestPublishedVersionNumber: null,
                latestPublishedVersionId: null,
            },
        ]);
        render(<BlueprintInformationRequestTab scope={"PERSONAL"}
                                               templateVersionId={undefined}
                                               onChange={vi.fn()}/>);

        expect(await screen.findByRole("option", {name: "Periodic records"})).toBeTruthy();
        expect(screen.queryByRole("option", {name: "Retired collection"})).toBeNull();
        expect(screen.queryByRole("option", {name: "Withdrawn version"})).toBeNull();
        expect(templates.getInformationRequestTemplate).not.toHaveBeenCalled();
    });

    it("states a failed read of the Templates instead of an empty list and lists them once Try again reads them", async () => {
        vi.mocked(templates.listInformationRequestTemplates)
            .mockRejectedValueOnce({errorMessage: "Request failed"})
            .mockResolvedValueOnce([summary("template-a", "Periodic records", InformationRequestTemplateScopeKind.ORGANIZATION)]);
        render(<BlueprintInformationRequestTab scope={"ORG"}
                                               templateVersionId={undefined}
                                               onChange={vi.fn()}/>);

        expect(await screen.findByText("The Templates this Blueprint can use could not be loaded.")).toBeTruthy();
        expect(screen.queryByText("No published Template is available in this workspace.")).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(await screen.findByRole("option", {name: "Periodic records"})).toBeTruthy();
        expect(screen.queryByText("The Templates this Blueprint can use could not be loaded.")).toBeNull();
    });

    it("tells authors to copy a platform Template into their own Templates before choosing it", async () => {
        render(<BlueprintInformationRequestTab scope={"ORG"}
                                               templateVersionId={undefined}
                                               onChange={vi.fn()}/>);

        await screen.findByRole("option", {name: "Periodic records"});
        expect(document.getElementById("blueprint-information-request-copy-hint")?.textContent)
            .toMatch(/copy it with Copy as a new Template/);
    });

    it("explains that a platform Blueprint cannot start an Information Request and offers no Template", async () => {
        render(<BlueprintInformationRequestTab scope={"APP"}
                                               templateVersionId={undefined}
                                               onChange={vi.fn()}/>);

        expect(screen.getByText(/A platform Blueprint cannot start an Information Request/)).toBeTruthy();
        expect(screen.queryByLabelText("Template")).toBeNull();
        expect(templates.listInformationRequestTemplates).not.toHaveBeenCalled();
    });

    it("names the pinned Version and removes it on request", async () =>
    {
        const onChange = vi.fn();
        render(<BlueprintInformationRequestTab scope={"PERSONAL"}
                                               templateVersionId={"template-a-version-3"}
                                               onChange={onChange}/>);

        expect(await screen.findByText("Exchanges started from this Blueprint create a draft Information Request from Periodic records, version 3.")).toBeTruthy();
        expect(screen.getByText(
            "Starting an Exchange from this Blueprint creates a draft Information Request from one published Template Version, " +
            "with the person who starts it as Decision Maker. Exchange documents become optional because the request collects them. " +
            "Changing it affects only Exchanges started afterwards.",
        )).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Remove the Information Request"}));

        expect(onChange).toHaveBeenCalledWith(undefined);
        expect(templates.listInformationRequestTemplates).toHaveBeenCalledWith({scopeKind: InformationRequestTemplateScopeKind.PERSONAL});
    });

    it("names a retired pinned Version and says why new Exchanges cannot start the request", async () => {
        render(<BlueprintInformationRequestTab scope={"ORG"}
                                               templateVersionId={"retired-version"}
                                               pinnedVersion={{
                                                   templateId: "template-r",
                                                   templateDisplayName: "Retired collection",
                                                   versionNumber: 2,
                                                   status: InformationRequestTemplateStatus.RETIRED,
                                                   latest: false,
                                                   retiredAt: Date.parse("2026-09-20T00:00:00Z"),
                                               }}
                                               onChange={vi.fn()}/>);

        await screen.findByRole("option", {name: "Periodic records"});
        expect(screen.getByText("Exchanges started from this Blueprint create a draft Information Request from Retired collection, version 2.")).toBeTruthy();
        expect(document.getElementById("blueprint-information-request-pinned-state")?.textContent).toBe("Retired");
        expect(document.getElementById("blueprint-information-request-version-notice")?.textContent)
            .toContain("Version 2 of Retired collection was retired. New Exchanges cannot start this request until you choose a currently published Template.");
    });

    it("says when a newer Version of the pinned Template is published", async () => {
        render(<BlueprintInformationRequestTab scope={"ORG"}
                                               templateVersionId={"template-a-version-2"}
                                               pinnedVersion={{
                                                   templateId: "template-a",
                                                   templateDisplayName: "Periodic records",
                                                   versionNumber: 2,
                                                   status: InformationRequestTemplateStatus.PUBLISHED,
                                                   latest: false,
                                                   latestPublishedVersionNumber: 3,
                                               }}
                                               onChange={vi.fn()}/>);

        await screen.findByRole("option", {name: "Periodic records"});
        expect(screen.getByText("Exchanges started from this Blueprint create a draft Information Request from Periodic records, version 2.")).toBeTruthy();
        expect(document.getElementById("blueprint-information-request-pinned-state")?.textContent).toBe("Earlier version");
        expect(document.getElementById("blueprint-information-request-version-notice")?.textContent)
            .toContain("Version 3 of Periodic records is published. Exchanges keep using version 2 until you choose the Template again.");
    });

    it("shows no notice for the latest published Version", async () => {
        render(<BlueprintInformationRequestTab scope={"PERSONAL"}
                                               templateVersionId={"template-a-version-3"}
                                               pinnedVersion={{
                                                   templateId: "template-a",
                                                   templateDisplayName: "Periodic records",
                                                   versionNumber: 3,
                                                   status: InformationRequestTemplateStatus.PUBLISHED,
                                                   latest: true,
                                                   latestPublishedVersionNumber: 3,
                                               }}
                                               onChange={vi.fn()}/>);

        expect(await screen.findByText("Exchanges started from this Blueprint create a draft Information Request from Periodic records, version 3.")).toBeTruthy();
        expect(document.getElementById("blueprint-information-request-version-notice")).toBeNull();
        expect(document.getElementById("blueprint-information-request-pinned-state")).toBeNull();
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
