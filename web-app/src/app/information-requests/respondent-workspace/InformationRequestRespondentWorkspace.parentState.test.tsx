/** @vitest-environment jsdom */
import {cleanup, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../../../services/apiClient.ts";
import {InformationRequestResponseWorkspaceDto} from "../../models/models.tsx";
import {startPendingWorkServer, workspaceNow} from "./testing/pendingWorkServer.ts";
import {openRespondentShell, respondentShells, seedVerifiedAccessLink} from "./testing/respondentShells.tsx";

vi.mock("../../../services/apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn(), patch: vi.fn()}}));
vi.mock("../reusable-answer/useReusableAnswers.ts", () => ({useReusableAnswers: (): null => null}));
vi.mock("./account-link-offer/AccountLinkOffer.tsx", () => ({default: (): null => null}));

const COUNT_FIELD = "information-request-response-field-root-record-count";
const NOTICE = "information-request-respondent-exchange-acceptance";

const NOT_ACCEPTED = "The Exchange holding this request has not been accepted yet. You can read the request; answers, " +
    "files and submissions are accepted once the Exchange is accepted.";

const field = (): HTMLInputElement => {
    const found = document.getElementById(COUNT_FIELD);
    if (!found) throw new Error(`Missing element ${COUNT_FIELD}`);
    return found as HTMLInputElement;
};

const serveWorkspace = (change: (workspace: InformationRequestResponseWorkspaceDto) => void) => {
    const previousGet = vi.mocked(apiClient.get).getMockImplementation();
    vi.mocked(apiClient.get).mockImplementation(async (path, config) => {
        if (String(path).endsWith("/response-workspace")) {
            const workspace = workspaceNow();
            change(workspace);
            return {data: workspace};
        }
        if (previousGet) return previousGet(path, config);
        throw new Error(`Unexpected read ${String(path)}`);
    });
};

describe("InformationRequestRespondentWorkspace before the Exchange is accepted, on both respondent shells", () => {
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

    it.each(respondentShells)("says the Exchange is not accepted yet and keeps the answers read-only until it is, in the $mode shell", async shell => {
        startPendingWorkServer();
        serveWorkspace(workspace => {
            workspace.exchangeAccepted = false;
        });
        openRespondentShell(shell);

        await waitFor(() => expect(document.getElementById(`${NOTICE}-message-body`)?.textContent).toBe(NOT_ACCEPTED));
        expect(field().disabled).toBe(true);
        expect(document.getElementById("information-request-response-save-status")?.textContent)
            .toBe("Answers cannot be changed now.");
    });

    it.each(respondentShells)("shows no acceptance notice and keeps the answers editable once the Exchange is accepted, in the $mode shell", async shell => {
        startPendingWorkServer();
        serveWorkspace(workspace => {
            workspace.exchangeAccepted = true;
        });
        openRespondentShell(shell);

        await waitFor(() => expect(field().disabled).toBe(false));
        expect(document.getElementById(NOTICE)).toBeNull();
    });
});
