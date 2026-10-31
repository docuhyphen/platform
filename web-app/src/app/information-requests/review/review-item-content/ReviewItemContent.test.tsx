/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import * as evidenceTransport from "../../../../services/informationRequestEvidenceService.ts";
import {InformationRequestEvidenceContentUse} from "../../../../services/informationRequestEvidenceService.ts";
import {InformationRequestRequirementType, InformationRequestReviewItemDto} from "../../../models/models.tsx";
import ReviewItemContent from "./ReviewItemContent.tsx";

vi.mock("../../../../services/informationRequestEvidenceService.ts", () => ({openInformationRequestSubmittedEvidenceContent: vi.fn()}));

const submitted: InformationRequestReviewItemDto = {
    submissionItemId: "item-a",
    requirementId: "requirement-a",
    requirementKey: "supporting-record",
    requirementType: InformationRequestRequirementType.DOCUMENT,
    occurrencePath: "root",
    reviewed: true,
    contentVisible: true,
    waiverPermitted: false,
    evidence: [{artifactId: "artifact-a", evidenceVersionId: "version-a", versionNumber: 1, conformance: "CONFORMING"}],
};

const otherItem: InformationRequestReviewItemDto = {
    ...submitted,
    submissionItemId: "item-b",
    requirementId: "requirement-b",
    evidence: [{artifactId: "artifact-b", evidenceVersionId: "version-b", versionNumber: 1, conformance: "CONFORMING"}],
};

const hiddenItem: InformationRequestReviewItemDto = {...submitted, contentVisible: false, evidence: []};

const USES: InformationRequestEvidenceContentUse[] = ["preview", "content"];

const buttonName = (use: InformationRequestEvidenceContentUse): string =>
    `${use === "preview" ? "Preview" : "Download"} version 1 of Supporting record`;

const contentOf = (item: InformationRequestReviewItemDto, requestId: string = "request-a", packageId: string = "package-a") => (
    <ReviewItemContent requestId={requestId}
                       packageId={packageId}
                       item={item}
                       label={"Supporting record"}/>
);

interface PendingFile {
    answer: (file: Blob) => void;
    refuse: (refusal: unknown) => void;
}

const settle = () => new Promise<void>(resolve => setTimeout(resolve, 0));

describe("ReviewItemContent opening a submitted file", () => {
    let pending: PendingFile[];
    let downloads: { href: string; download: string }[];

    const deliver = () => act(async () => {
        pending.splice(0).forEach(file => file.answer(new Blob(["submitted record"], {type: "application/pdf"})));
        await settle();
    });

    const refuse = () => act(async () => {
        pending.splice(0).forEach(file => file.refuse({errorMessage: "The file could not be read"}));
        await settle();
    });

    const expectNothingOpened = () => {
        expect.soft(URL.createObjectURL).not.toHaveBeenCalled();
        expect.soft(window.open).not.toHaveBeenCalled();
        expect.soft(downloads).toEqual([]);
    };

    it("previews inside a named dialog without opening a browser window", async () => {
        render(contentOf(submitted));
        fireEvent.click(screen.getByRole("button", {name: buttonName("preview")}));
        await deliver();
        expect(screen.getByRole("dialog", {name: "Preview File version 1"})).toBeTruthy();
        expect(screen.getByTitle("Preview of File version 1").getAttribute("src")).toBe("blob:submitted-record");
        expect(window.open).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole("button", {name: "Close preview"}));
        expect(screen.queryByRole("dialog", {name: "Preview File version 1"})).toBeNull();
        expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:submitted-record");
    });
    beforeEach(() => {
        vi.resetAllMocks();
        pending = [];
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
        downloads = [];
        Object.assign(URL, {createObjectURL: vi.fn(() => "blob:submitted-record"), revokeObjectURL: vi.fn()});
        vi.spyOn(window, "open").mockImplementation(() => null);
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
            downloads.push({href: this.href, download: this.download});
        });
        vi.mocked(evidenceTransport.openInformationRequestSubmittedEvidenceContent).mockImplementation(() =>
            new Promise<Blob>((answer, refuse) => pending.push({answer, refuse})));
    });

    afterEach(() => {
        cleanup();
        vi.restoreAllMocks();
    });

    it("previews the exact submitted file version in the review", async () => {
        render(contentOf(submitted));

        fireEvent.click(screen.getByRole("button", {name: buttonName("preview")}));
        await deliver();

        expect(evidenceTransport.openInformationRequestSubmittedEvidenceContent)
            .toHaveBeenCalledWith("request-a", "package-a", "version-a", "preview");
        expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
        expect(screen.getByTitle("Preview of File version 1").getAttribute("src")).toBe("blob:submitted-record");
        expect(window.open).not.toHaveBeenCalled();
        expect(downloads).toEqual([]);
    });

    it("downloads and names a submitted file by the name it was uploaded with", async () => {
        const named: InformationRequestReviewItemDto = {
            ...submitted,
            evidence: [{...submitted.evidence[0], declaredFileName: "supporting-record.pdf", contentLength: 2048}],
        };
        render(contentOf(named));

        expect(screen.getByText("supporting-record.pdf, version 1, 2 KiB, conforming")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: buttonName("content")}));
        await deliver();

        expect(downloads).toEqual([{href: "blob:submitted-record", download: "supporting-record.pdf"}]);
    });

    it("downloads a submitted file without a stated name under its version name", async () => {
        render(contentOf(submitted));

        fireEvent.click(screen.getByRole("button", {name: buttonName("content")}));
        await deliver();

        expect(evidenceTransport.openInformationRequestSubmittedEvidenceContent)
            .toHaveBeenCalledWith("request-a", "package-a", "version-a", "content");
        expect(downloads).toEqual([{href: "blob:submitted-record", download: "evidence-version-1"}]);
        expect(window.open).not.toHaveBeenCalled();
    });

    it.each(USES)("opens nothing for a %s whose file arrives after the item content closed", async use => {
        const view = render(contentOf(submitted));
        fireEvent.click(screen.getByRole("button", {name: buttonName(use)}));

        view.unmount();
        await deliver();

        expectNothingOpened();
    });

    it.each(USES)("opens nothing for a %s whose file arrives after the content shows another request", async use => {
        const view = render(contentOf(submitted));
        fireEvent.click(screen.getByRole("button", {name: buttonName(use)}));

        view.rerender(contentOf(submitted, "request-b"));
        await deliver();

        expectNothingOpened();
    });

    it.each(USES)("opens nothing for a %s whose file arrives after the content shows another package", async use => {
        const view = render(contentOf(submitted));
        fireEvent.click(screen.getByRole("button", {name: buttonName(use)}));

        view.rerender(contentOf(submitted, "request-a", "package-b"));
        await deliver();

        expectNothingOpened();
    });

    it.each(USES)("opens nothing for a %s whose file arrives after the content shows another item", async use => {
        const view = render(contentOf(submitted));
        fireEvent.click(screen.getByRole("button", {name: buttonName(use)}));

        view.rerender(contentOf(otherItem));
        await deliver();

        expectNothingOpened();
    });

    it.each(USES)("opens nothing for a %s whose file arrives after the item's content is no longer shown", async use => {
        const view = render(contentOf(submitted));
        fireEvent.click(screen.getByRole("button", {name: buttonName(use)}));

        view.rerender(contentOf(hiddenItem));
        await deliver();

        expect(screen.getByText("The submitted content is currently withheld.")).toBeTruthy();
        expectNothingOpened();
    });

    it.each(USES)("opens a %s whose file arrives after the same item is shown again", async use => {
        const view = render(contentOf(submitted));
        fireEvent.click(screen.getByRole("button", {name: buttonName(use)}));

        view.rerender(contentOf({...submitted, evidence: submitted.evidence.map(file => ({...file}))}));
        await deliver();

        expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
        expect(use === "preview" ? screen.getAllByTitle("Preview of File version 1").length : downloads.length).toBe(1);
    });

    it("says a file could not be opened for the item that asked for it", async () => {
        render(contentOf(submitted));
        fireEvent.click(screen.getByRole("button", {name: buttonName("preview")}));

        await refuse();

        expect(screen.getByRole("alert").textContent).toBe("The file could not be opened.");
        expectNothingOpened();
    });

    it("says nothing about a refused file once the content shows another item", async () => {
        const view = render(contentOf(submitted));
        fireEvent.click(screen.getByRole("button", {name: buttonName("preview")}));

        view.rerender(contentOf(otherItem));
        await refuse();

        expect(screen.queryByRole("alert")).toBeNull();
        expectNothingOpened();
    });
});

describe("ReviewItemContent showing what the server sends as null", () => {
    afterEach(cleanup);

    it("shows no value line for an item without a value and no size for a file without a length", () => {
        render(contentOf({
            ...submitted,
            fieldValue: null,
            narrative: null,
            evidence: [{
                artifactId: "artifact-a",
                evidenceVersionId: "version-a",
                versionNumber: 1,
                contentLength: null,
                conformance: "CONFORMING"
            }],
        }));

        expect(document.getElementById("information-request-review-item-item-a-content-value")).toBeNull();
        expect(screen.queryByText("No value")).toBeNull();
        expect(screen.getByText("File version 1, conforming")).toBeTruthy();
    });

    it("still shows a value and a size the server sends, including zero", () => {
        render(contentOf({
            ...submitted,
            requirementType: InformationRequestRequirementType.FIELD,
            fieldValue: 0,
            evidence: [{
                artifactId: "artifact-a",
                evidenceVersionId: "version-a",
                versionNumber: 1,
                contentLength: 0,
                conformance: "CONFORMING"
            }],
        }));

        expect(screen.getByText("0")).toBeTruthy();
        expect(screen.getByText("File version 1, 0 bytes, conforming")).toBeTruthy();
    });
});
