/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../../../services/apiClient.ts";
import {
    amendedText,
    answerField,
    changeRespondent,
    deliverHeldReads,
    dropped,
    element,
    evidenceSuffix,
    FIRST,
    heldSuffixes,
    keptText,
    openRespondentPage,
    panelId,
    projections,
    readFor,
    reads,
    requestPath,
    respondentChanged,
    reviewDialogId,
    saveAnswer,
    SECOND,
    server,
    submitButton,
    submitted,
} from "./testing/respondentChangeFixture.tsx";

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn(), patch: vi.fn()}}));

describe("InformationRequestRespondentWorkspace after the workspace names another respondent", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
        Element.prototype.scrollIntoView = vi.fn();
    });

    beforeEach(() => {
        vi.resetAllMocks();
        window.sessionStorage.clear();
        Object.assign(server, {
            caller: FIRST,
            callerAtNextSave: SECOND,
            holdReadsOf: null,
            held: [],
            submissionAnswers: []
        });
        vi.mocked(apiClient.get).mockImplementation(async path => {
            const caller = server.caller;
            if (server.holdReadsOf === caller && heldSuffixes.some(suffix => String(path).endsWith(suffix))) {
                return new Promise(resolve => server.held.push(() => resolve(readFor(caller, String(path)))));
            }
            return readFor(caller, String(path));
        });
        vi.mocked(apiClient.patch).mockImplementation(async (path, body) => {
            if (path !== `${requestPath}/responses`) throw new Error(`Unexpected command ${path}`);
            if (server.callerAtNextSave) {
                server.caller = server.callerAtNextSave;
                server.callerAtNextSave = null;
            }
            if ((body as { respondentKey?: string }).respondentKey !== server.caller) throw respondentChanged;
            return {data: [], headers: {etag: "\"responses:1\""}};
        });
        vi.mocked(apiClient.post).mockImplementation(async path => {
            if (path === `${requestPath}${evidenceSuffix}`) throw dropped;
            if (path === `${requestPath}/submissions`) {
                return new Promise(resolve => server.submissionAnswers.push(() =>
                    resolve({data: submitted, headers: {etag: submitted.responseETag}})));
            }
            throw new Error(`Unexpected command ${path}`);
        });
    });

    afterEach(cleanup);

    it("shows only the new respondent's carried answers, accepted answers, amendments, and review results", async () => {
        openRespondentPage();
        expect(await screen.findByText(projections[FIRST].carried)).toBeTruthy();
        expect(await screen.findByText(projections[FIRST].offered)).toBeTruthy();
        expect(await screen.findByText(amendedText(FIRST))).toBeTruthy();
        expect(await screen.findByText(projections[FIRST].finding)).toBeTruthy();

        await changeRespondent();

        expect(screen.queryByText(projections[FIRST].carried)).toBeNull();
        expect(screen.queryByText(projections[FIRST].offered)).toBeNull();
        expect(screen.queryByText(amendedText(FIRST))).toBeNull();
        expect(screen.queryByText(projections[FIRST].finding)).toBeNull();
        expect(await screen.findByText(projections[SECOND].carried)).toBeTruthy();
        expect(await screen.findByText(projections[SECOND].offered)).toBeTruthy();
        expect(await screen.findByText(amendedText(SECOND))).toBeTruthy();
        expect(await screen.findByText(projections[SECOND].finding)).toBeTruthy();
        heldSuffixes.forEach(suffix => expect(reads(suffix)).toHaveLength(2));
    });

    it("drops the earlier respondent's projections as soon as the workspace names another, before the new reads answer", async () => {
        openRespondentPage();
        expect(await screen.findByText(projections[FIRST].carried)).toBeTruthy();
        expect(await screen.findByText(projections[FIRST].offered)).toBeTruthy();
        expect(await screen.findByText(amendedText(FIRST))).toBeTruthy();
        expect(await screen.findByText(projections[FIRST].finding)).toBeTruthy();
        await waitFor(() => expect(submitButton()?.disabled).toBe(false));
        server.holdReadsOf = SECOND;

        await changeRespondent();

        expect(screen.queryByText(projections[FIRST].carried)).toBeNull();
        expect(screen.queryByText(projections[FIRST].offered)).toBeNull();
        expect(screen.queryByText(amendedText(FIRST))).toBeNull();
        expect(screen.queryByText(projections[FIRST].finding)).toBeNull();
        expect(submitButton()).toBeNull();
        await waitFor(() => expect(server.held).toHaveLength(heldSuffixes.length));
        await deliverHeldReads();
        expect(await screen.findByText(projections[SECOND].carried)).toBeTruthy();
        await waitFor(() => expect(submitButton()?.disabled).toBe(true));
    });

    it("reads submission permission again for the new respondent although the response ETag is unchanged", async () => {
        openRespondentPage();
        await waitFor(() => expect(submitButton()?.disabled).toBe(false));

        await changeRespondent();

        await waitFor(() => expect(submitButton()?.disabled).toBe(true));
        expect(screen.getByText("Submission is unavailable for your current access or this request's standing. Refresh the request or contact its administrator.")).toBeTruthy();
        expect(reads("/submission-preview")).toHaveLength(2);
    });

    it("shows nothing from reads the earlier respondent started before the workspace named another", async () => {
        server.holdReadsOf = FIRST;
        openRespondentPage();
        await screen.findByRole("heading", {name: "Periodic records request"});
        await waitFor(() => expect(server.held).toHaveLength(heldSuffixes.length));

        await changeRespondent();
        await deliverHeldReads();

        expect(screen.queryByText(projections[FIRST].carried)).toBeNull();
        expect(screen.queryByText(projections[FIRST].offered)).toBeNull();
        expect(screen.queryByText(amendedText(FIRST))).toBeNull();
        expect(screen.queryByText(projections[FIRST].finding)).toBeNull();
        expect(submitButton()?.disabled ?? true).toBe(true);
        expect(await screen.findByText(projections[SECOND].carried)).toBeTruthy();
        expect(await screen.findByText(projections[SECOND].finding)).toBeTruthy();
        await waitFor(() => expect(submitButton()?.disabled).toBe(true));
    });

    it("shows no submission result that answers the earlier respondent after the workspace named another", async () => {
        openRespondentPage();
        await waitFor(() => expect(submitButton()?.disabled).toBe(false));
        fireEvent.click(submitButton() as HTMLButtonElement);
        fireEvent.click(element(`${reviewDialogId}-confirm`));
        await waitFor(() => expect(server.submissionAnswers).toHaveLength(1));

        await changeRespondent();
        await act(async () => server.submissionAnswers.splice(0).forEach(answer => answer()));

        expect(screen.queryByText("Submission 1 was recorded.")).toBeNull();
        expect(document.getElementById("information-request-submission-result")).toBeNull();
        await waitFor(() => expect(submitButton()?.disabled).toBe(true));
        expect(screen.queryByText("Submission 1 was recorded.")).toBeNull();
    });

    it("closes a submission review the earlier respondent opened", async () => {
        openRespondentPage();
        await waitFor(() => expect(submitButton()?.disabled).toBe(false));
        fireEvent.click(submitButton() as HTMLButtonElement);
        await waitFor(() => expect(document.getElementById(reviewDialogId)).not.toBeNull());

        await changeRespondent();

        await waitFor(() => expect(document.getElementById(reviewDialogId)).toBeNull());
        await waitFor(() => expect(submitButton()?.disabled).toBe(true));
    });

    it("keeps a file that was not sent only for the respondent who chose it", async () => {
        openRespondentPage();
        expect(await screen.findByText("No files yet.")).toBeTruthy();
        const file = new File(["record"], "record-of-the-first-respondent.pdf", {type: "application/pdf"});
        fireEvent.change(element(`${panelId}-upload-button-input`), {target: {files: [file]}});
        expect(await screen.findByText(keptText(file.name))).toBeTruthy();

        await changeRespondent();
        await waitFor(() => expect(reads(evidenceSuffix)).toHaveLength(3));
        await waitFor(() => expect(document.getElementById(`${panelId}-empty`)).not.toBeNull());

        expect(document.getElementById(`${panelId}-kept`)).toBeNull();
        expect(screen.queryByText(keptText(file.name))).toBeNull();

        server.callerAtNextSave = FIRST;
        await saveAnswer("Unsaved answer of the second respondent");

        expect(await screen.findByText(keptText(file.name))).toBeTruthy();
        expect(answerField()?.value).toBe("Unsaved answer of the first respondent");
    });

    it("keeps an open submission review and reads nothing again when a refresh names the same respondent", async () => {
        server.callerAtNextSave = null;
        openRespondentPage();
        expect(await screen.findByText(projections[FIRST].carried)).toBeTruthy();
        await waitFor(() => expect(submitButton()?.disabled).toBe(false));
        fireEvent.click(submitButton() as HTMLButtonElement);
        await waitFor(() => expect(document.getElementById(reviewDialogId)).not.toBeNull());

        await saveAnswer("Saved answer of the first respondent");
        expect(await screen.findByText("All changes saved.")).toBeTruthy();

        expect(document.getElementById(reviewDialogId)).not.toBeNull();
        expect(screen.getByText(projections[FIRST].carried)).toBeTruthy();
        heldSuffixes.forEach(suffix => expect(reads(suffix)).toHaveLength(1));
    });
});
