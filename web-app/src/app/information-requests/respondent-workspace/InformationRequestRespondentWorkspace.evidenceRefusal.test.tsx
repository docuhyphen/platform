/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../../../services/apiClient.ts";
import {InformationRequestEvidenceContentUse} from "../../../services/informationRequestEvidenceService.ts";
import {
    openingPreviewTab,
    PreviewTabDouble,
    previewTabDouble
} from "../requirement-evidence/testing/previewTabDouble.ts";
import {
    storeInformationRequestAccessToken,
    storeInformationRequestSession,
} from "../../../services/informationRequestRuntimeService.ts";
import {
    FILE_ROUTE_REFUSAL_TYPE,
    fileAnswer,
    jsonAnswer,
    LocalAnswer,
    LocalApiServer,
    startLocalApiServer,
    textOf,
} from "../../../services/testing/localApiServer.ts";
import {
    InformationRequestEvidenceArtifactDto,
    InformationRequestEvidenceCollectionState,
    InformationRequestEvidenceSourceKind,
} from "../../models/models.tsx";
import InformationRequestRespondentWorkspace from "./InformationRequestRespondentWorkspace.tsx";
import {evidenceSuffix, FIRST, noFiles, panelId, readFor, requestPath} from "./testing/respondentChangeFixture.tsx";

vi.mock("./account-link-offer/AccountLinkOffer.tsx", () => ({default: () => null}));

const EXPIRED = {
    reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED",
    errorMessage: "This request access session has expired",
};
const REVOKED = {
    reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_REVOKED",
    errorMessage: "This request access session has been revoked",
};
const NOT_RELEASED = {
    reasonCode: "INFORMATION_REQUEST_EVIDENCE_CONTENT_NOT_RELEASED",
    errorMessage: "This evidence is released once it has passed a malware scan",
};
const sessionKey = "information-request-session:request-a";
const contactProofId = "information-request-contact-proof";
const rowId = `${panelId}-artifact-record`;
const objectUrl = "blob:supporting-record";
const uses: InformationRequestEvidenceContentUse[] = ["content", "preview"];

const recordFile: InformationRequestEvidenceArtifactDto = {
    id: "artifact-a",
    requirementId: "runtime-2",
    artifactKey: "record",
    collectionState: InformationRequestEvidenceCollectionState.ACTIVE,
    artifactRevision: 1,
    etag: "\"artifact-a:1\"",
    createdByCaller: true,
    changeableByCaller: true,
    createdAt: Date.parse("2026-10-14T08:00:00Z"),
    updatedAt: Date.parse("2026-10-14T08:00:00Z"),
    versions: [{
        id: "version-a",
        versionNumber: 1,
        sourceKind: InformationRequestEvidenceSourceKind.DOCUMENT_VERSION,
        declaredFileName: "record.pdf",
        declaredMediaType: "application/pdf",
        createdByCaller: true,
        createdAt: Date.parse("2026-10-14T08:00:00Z"),
        findings: [],
    }],
};

const opening = {answer: fileAnswer("%PDF-1.4 supporting record") as LocalAnswer};
let server: LocalApiServer;

const answerPage = (path: string): LocalAnswer => {
    const read = path.replace(/^\/no-auth/, "");
    if (read.startsWith(`${requestPath}${evidenceSuffix}/artifact-a/versions/version-a/`)) return opening.answer;
    if (read === `${requestPath}${evidenceSuffix}`) return jsonAnswer(200, {...noFiles, artifacts: [recordFile]});
    try {
        return jsonAnswer(200, readFor(FIRST, read).data);
    } catch {
        return jsonAnswer(404, {reasonCode: "INFORMATION_REQUEST_NOT_FOUND", errorMessage: "Not found"});
    }
};

const openVerifiedPage = async () => {
    storeInformationRequestAccessToken("request-a", "bootstrap-a");
    storeInformationRequestSession("request-a", {sessionId: "session-a", sessionToken: "session-secret"});
    render(
        <MemoryRouter initialEntries={["/request-link?s=request-a"]}>
            <Routes>
                <Route path={"/request-link"}
                       element={<InformationRequestRespondentWorkspace accessMode={"no-auth"}/>}/>
            </Routes>
        </MemoryRouter>,
    );
    await screen.findByText("record.pdf");
};

const select = (use: InformationRequestEvidenceContentUse) => act(async () => {
    fireEvent.click(document.getElementById(`${rowId}-${use === "preview" ? "preview" : "download"}`) as HTMLElement);
});

const panelMessage = () => document.getElementById(`${panelId}-message-body`)?.textContent ?? null;

describe("InformationRequestRespondentWorkspace when the server refuses a file", () => {
    let windowOpened: ReturnType<typeof vi.spyOn>;
    let tab: PreviewTabDouble;
    let downloads: { href: string; download: string }[];

    beforeAll(async () => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
        Element.prototype.scrollIntoView = vi.fn();
        Object.assign(URL, {createObjectURL: vi.fn(), revokeObjectURL: vi.fn()});
        server = await startLocalApiServer((_, path) => answerPage(path));
        apiClient.defaults.adapter = "xhr";
        apiClient.defaults.baseURL = server.origin;
    });

    afterAll(() => server.close());

    beforeEach(() => {
        vi.mocked(URL.createObjectURL).mockReset().mockReturnValue(objectUrl);
        window.sessionStorage.clear();
        server.served.splice(0);
        downloads = [];
        tab = previewTabDouble();
        windowOpened = openingPreviewTab(tab);
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
            downloads.push({href: this.href, download: this.download});
        });
    });

    afterEach(() => {
        cleanup();
        vi.restoreAllMocks();
    });

    it.each(uses.flatMap(use => [
        {use, ended: "expired", refusal: EXPIRED},
        {use, ended: "revoked", refusal: REVOKED},
    ]))("returns to verification when a $use is refused because its session $ended", async ({use, refusal}) => {
        opening.answer = jsonAnswer(409, refusal, FILE_ROUTE_REFUSAL_TYPE);
        await openVerifiedPage();

        await select(use);
        await waitFor(() => expect(document.getElementById(contactProofId) ?? panelMessage()).toBeTruthy());

        expect(document.getElementById(contactProofId)).not.toBeNull();
        expect(window.sessionStorage.getItem(sessionKey)).toBe("");
        expect(document.getElementById(panelId)).toBeNull();
        expect(screen.getByText(refusal.errorMessage)).toBeTruthy();
        expect(URL.createObjectURL).not.toHaveBeenCalled();
        expect(tab.location.replace).not.toHaveBeenCalled();
        expect(tab.close).toHaveBeenCalledTimes(windowOpened.mock.calls.length);
        expect(downloads).toEqual([]);
    });

    it.each(uses)("says why the server refused a %s and keeps the verified session", async use => {
        opening.answer = jsonAnswer(409, NOT_RELEASED, FILE_ROUTE_REFUSAL_TYPE);
        await openVerifiedPage();

        await select(use);
        await waitFor(() => expect(panelMessage()).not.toBeNull());

        expect(panelMessage()).toBe("This file can be opened once it has passed a malware scan.");
        expect(window.sessionStorage.getItem(sessionKey)).toBe("session-secret");
        expect(document.getElementById(contactProofId)).toBeNull();
        expect(screen.getByText("record.pdf")).toBeTruthy();
        expect(URL.createObjectURL).not.toHaveBeenCalled();
    });

    it("falls back to its own words when a refusal cannot be read, and keeps the verified session", async () => {
        opening.answer = {status: 502, contentType: "text/html", body: "<html><body>Bad gateway</body></html>"};
        await openVerifiedPage();

        await select("content");
        await waitFor(() => expect(panelMessage()).not.toBeNull());

        expect(panelMessage()).toBe("The file could not be opened.");
        expect(window.sessionStorage.getItem(sessionKey)).toBe("session-secret");
        expect(document.getElementById(contactProofId)).toBeNull();
        expect(downloads).toEqual([]);
    });

    it.each(uses)("opens the file the server sends for a %s", async use => {
        opening.answer = fileAnswer("%PDF-1.4 supporting record");
        await openVerifiedPage();

        await select(use);
        await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalledTimes(1));

        const content = vi.mocked(URL.createObjectURL).mock.calls[0][0] as Blob;
        expect(await textOf(content)).toBe("%PDF-1.4 supporting record");
        if (use === "preview") expect(tab.location.replace).toHaveBeenCalledWith(objectUrl);
        else expect(downloads).toEqual([{href: objectUrl, download: "record.pdf"}]);
        expect(window.sessionStorage.getItem(sessionKey)).toBe("session-secret");
    });
});
