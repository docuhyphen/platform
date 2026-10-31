/** @vitest-environment jsdom */
import {cleanup, fireEvent, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {pendingWorkServer, startPendingWorkServer, submissionsSent} from "./testing/pendingWorkServer.ts";
import {openRespondentShell, respondentShells, seedVerifiedAccessLink} from "./testing/respondentShells.tsx";

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn(), patch: vi.fn()}}));
vi.mock("../reusable-answer/useReusableAnswers.ts", () => ({useReusableAnswers: () => null}));
vi.mock("./account-link-offer/AccountLinkOffer.tsx", () => ({default: () => null}));

const REVIEW = "information-request-submission-review";

const element = (id: string): HTMLElement => {
    const found = document.getElementById(id);
    if (!found) throw new Error(`Missing element ${id}`);
    return found;
};

const submitButton = () => element("information-request-submission-submit") as HTMLButtonElement;

const reviewedItems = () => Array.from(element(`${REVIEW}-items`).querySelectorAll("li")).map(item => item.id.replace(`${REVIEW}-`, ""));

const openReview = async () => {
    await waitFor(() => expect(submitButton().disabled).toBe(false));
    fireEvent.click(submitButton());
    await waitFor(() => expect(document.getElementById(REVIEW)).not.toBeNull());
};

describe("InformationRequestRespondentWorkspace review of the submitted scope on both respondent shells", () => {
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

    it.each(respondentShells)("reviews the current server answers after another contributor changes them in the $mode shell", async shell => {
        startPendingWorkServer({staged: true});
        openRespondentShell(shell);
        await waitFor(() => expect(submitButton().disabled).toBe(false));
        pendingWorkServer.count = "Updated by another contributor";
        pendingWorkServer.revision += 1;
        fireEvent.click(element("information-request-submission-stage"));
        fireEvent.click(element("information-request-submission-stage-second"));
        await waitFor(() => expect(element("information-request-submission-stage").textContent).toContain("second"));
        fireEvent.click(element("information-request-submission-stage"));
        fireEvent.click(element("information-request-submission-stage-first"));
        await waitFor(() => expect(element("information-request-submission-stage").textContent).toContain("first"));
        await openReview();
        expect(element(`${REVIEW}-runtime-1-answer`).textContent).toContain("Updated by another contributor");
    });
    it.each(respondentShells)("reviews only the part chosen after changing the part to submit, in the $mode shell", async shell => {
        startPendingWorkServer({staged: true});
        openRespondentShell(shell);
        await waitFor(() => expect(submitButton().disabled).toBe(false));

        fireEvent.click(element("information-request-submission-stage"));
        fireEvent.click(element("information-request-submission-stage-second"));
        await waitFor(() => expect(element("information-request-submission-stage").textContent).toContain("second"));
        await openReview();

        expect(reviewedItems()).toEqual(["runtime-2", "runtime-3"]);
        fireEvent.click(element(`${REVIEW}-confirm`));
        await waitFor(() => expect(submissionsSent()).toHaveLength(1));
        expect(submissionsSent()[0].body).toEqual({stageKey: "second"});
    });

    it.each(respondentShells)("reviews the next part once an earlier part was submitted, in the $mode shell", async shell => {
        startPendingWorkServer({staged: true, submittedStages: ["first"]});
        openRespondentShell(shell);

        await openReview();

        expect(reviewedItems()).toEqual(["runtime-2", "runtime-3"]);
    });

    it.each(respondentShells)("reviews only the part returned for correction, in the $mode shell", async shell => {
        startPendingWorkServer({staged: true, correctionOpen: true});
        openRespondentShell(shell);
        await waitFor(() => expect(submitButton().textContent).toBe("Submit again"));

        await openReview();

        expect(reviewedItems()).toEqual(["runtime-1"]);
    });

    it.each(respondentShells)("reviews every answer of a request submitted as a whole, in the $mode shell", async shell => {
        startPendingWorkServer();
        openRespondentShell(shell);

        await openReview();

        expect(reviewedItems()).toEqual(["runtime-1", "runtime-2", "runtime-3"]);
    });
});
