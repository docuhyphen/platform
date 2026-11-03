/** @vitest-environment jsdom */
import {cleanup, fireEvent, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../../../services/apiClient.ts";
import {
    pendingWorkServer,
    savesSent,
    startPendingWorkServer,
    submissionsSent,
    uploadsSent,
} from "./testing/pendingWorkServer.ts";
import {openRespondentShell, respondentShells, seedVerifiedAccessLink} from "./testing/respondentShells.tsx";

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn(), patch: vi.fn()}}));
vi.mock("../reusable-answer/useReusableAnswers.ts", () => ({useReusableAnswers: (): null => null}));
vi.mock("./account-link-offer/AccountLinkOffer.tsx", () => ({default: (): null => null}));

const SUBMIT = "information-request-submission-submit";
const REVIEW = "information-request-submission-review";
const PENDING = "information-request-submission-pending";
const UPLOAD_INPUT = "information-request-evidence-root-supporting-record-PANEL-upload-button-input";

const element = (id: string): HTMLElement => {
    const found = document.getElementById(id);
    if (!found) throw new Error(`Missing element ${id}`);
    return found;
};

const submitButton = () => element(SUBMIT) as HTMLButtonElement;

const shownAnswer = (index: number) => element(`${REVIEW}-runtime-${index}-answer`).textContent;

const openReady = async (shell: (typeof respondentShells)[number]) => {
    openRespondentShell(shell);
    await waitFor(() => expect(submitButton().disabled).toBe(false));
};

const chooseUpload = async () => {
    const input = await waitFor(() => {
        const found = element(UPLOAD_INPUT) as HTMLInputElement;
        expect(found.disabled).toBe(false);
        return found;
    });
    fireEvent.change(input, {target: {files: [new File(["record"], "record.pdf", {type: "application/pdf"})]}});
};

describe("InformationRequestRespondentWorkspace pending work before submitting on both respondent shells", () => {
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
        seedVerifiedAccessLink();
    });

    afterEach(cleanup);

    it.each(respondentShells)("saves a changed answer before review and submits the saved answer that completes the request, in the $mode shell", async shell => {
        startPendingWorkServer();
        await openReady(shell);
        fireEvent.change(element("information-request-response-field-root-record-owner"), {target: {value: "Updated keeper"}});

        fireEvent.click(submitButton());

        await waitFor(() => expect(document.getElementById(REVIEW)).not.toBeNull());
        expect(savesSent()).toHaveLength(1);
        expect(shownAnswer(3)).toBe("Updated keeper. Note: Named by the keeper");
        fireEvent.click(element(`${REVIEW}-confirm`));
        await waitFor(() => expect(submissionsSent()).toHaveLength(1));
        expect(submissionsSent()[0].url).toBe(`${shell.base}/submissions`);
        expect(submissionsSent()[0].headers["If-Match"]).toBe("\"submission:2\"");
        await waitFor(() => expect(element("information-request-submission-result").textContent).toContain("This request is now complete."));
    });

    it.each(respondentShells)("keeps the review closed and states the save error when saving before review fails, in the $mode shell", async shell => {
        startPendingWorkServer();
        vi.mocked(apiClient.patch).mockRejectedValue(new Error("The answers could not be saved."));
        await openReady(shell);
        fireEvent.change(element("information-request-response-field-root-record-owner"), {target: {value: "Updated keeper"}});

        fireEvent.click(submitButton());

        await waitFor(() => expect(element("information-request-response-save-error").textContent).toBe("The answers could not be saved."));
        expect(document.getElementById(REVIEW)).toBeNull();
        await waitFor(() => expect(element(PENDING).textContent).toContain("not been saved"));
        expect(submitButton().disabled).toBe(true);
        expect(submissionsSent()).toHaveLength(0);
    });

    it.each(respondentShells)("holds Submit with its reason while a file is still uploading, in the $mode shell", async shell => {
        startPendingWorkServer();
        vi.mocked(apiClient.post).mockImplementation(() => new Promise(() => {
        }));
        await openReady(shell);

        await chooseUpload();

        await waitFor(() => expect(uploadsSent()).toHaveLength(1));
        await waitFor(() => expect(submitButton().disabled).toBe(true));
        expect(element(PENDING).textContent).toContain("still being sent");
        expect(submitButton().getAttribute("aria-describedby")).toBe(PENDING);
        fireEvent.click(submitButton());
        expect(document.getElementById(REVIEW)).toBeNull();
        expect(submissionsSent()).toHaveLength(0);
    });

    it.each(respondentShells)("holds Submit with its reason while a file that was not sent is kept, until it is discarded, in the $mode shell", async shell => {
        startPendingWorkServer();
        const submit = vi.mocked(apiClient.post).getMockImplementation();
        vi.mocked(apiClient.post).mockImplementation((url, body, config) =>
            String(url).endsWith("/evidence-artifacts")
                ? Promise.reject(new Error("The upload was interrupted."))
                : submit ? submit(url, body, config) : Promise.reject(new Error("No command handler")));
        await openReady(shell);

        await chooseUpload();

        await waitFor(() => expect(document.getElementById("information-request-evidence-root-supporting-record-PANEL-kept")).not.toBeNull());
        await waitFor(() => expect(submitButton().disabled).toBe(true));
        expect(element(PENDING).textContent).toContain("was not sent");
        fireEvent.click(element("information-request-evidence-root-supporting-record-PANEL-kept-1-discard"));
        await waitFor(() => expect(submitButton().disabled).toBe(false));
        expect(document.getElementById(PENDING)).toBeNull();
        fireEvent.click(submitButton());
        await waitFor(() => expect(document.getElementById(REVIEW)).not.toBeNull());
    });

    it.each(respondentShells)("saves a changed answer before reviewing the selected stage and submits that stage, in the $mode shell", async shell => {
        startPendingWorkServer({staged: true});
        await openReady(shell);
        fireEvent.change(element("information-request-response-field-root-record-count"), {target: {value: "Four records"}});

        fireEvent.click(submitButton());

        await waitFor(() => expect(document.getElementById(REVIEW)).not.toBeNull());
        expect(savesSent()).toHaveLength(1);
        expect(shownAnswer(1)).toBe("Partly provided: Four records. Reason: Two records remain unavailable");
        expect(document.getElementById(`${REVIEW}-runtime-3`)).toBeNull();
        fireEvent.click(element(`${REVIEW}-confirm`));
        await waitFor(() => expect(submissionsSent()).toHaveLength(1));
        expect(submissionsSent()[0].body).toEqual({stageKey: "first"});
        expect(submissionsSent()[0].headers["If-Match"]).toBe("\"submission:2\"");
    });

    it.each(respondentShells)("saves a changed answer before reviewing a correction and submits it again, in the $mode shell", async shell => {
        startPendingWorkServer({correctionOpen: true});
        await openReady(shell);
        expect(submitButton().textContent).toBe("Submit again");
        fireEvent.change(element("information-request-response-field-root-record-owner"), {target: {value: "Corrected keeper"}});

        fireEvent.click(submitButton());

        await waitFor(() => expect(document.getElementById(REVIEW)).not.toBeNull());
        expect(savesSent()).toHaveLength(1);
        expect(pendingWorkServer.owner).toBe("Corrected keeper");
        expect(shownAnswer(3)).toBe("Corrected keeper. Note: Named by the keeper");
        fireEvent.click(element(`${REVIEW}-confirm`));
        await waitFor(() => expect(submissionsSent()).toHaveLength(1));
        expect(submissionsSent()[0].headers["If-Match"]).toBe("\"submission:2\"");
    });
});
