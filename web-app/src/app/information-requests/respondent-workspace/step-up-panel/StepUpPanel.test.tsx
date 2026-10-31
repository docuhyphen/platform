/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import StepUpPanel from "./StepUpPanel.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {
    issueInformationRequestStepUpChallenge,
    verifyInformationRequestStepUp,
} from "../../../../services/informationRequestRuntimeService.ts";
import {requestStepUp} from "../../../../services/stepUpBroker.ts";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

vi.mock("../../../../services/informationRequestRuntimeService.ts", () => ({
    issueInformationRequestStepUpChallenge: vi.fn(),
    verifyInformationRequestStepUp: vi.fn(),
}));
vi.mock("../../../../services/stepUpBroker.ts", () => ({requestStepUp: vi.fn()}));

describe("StepUpPanel", () => {
    beforeAll(() => {
        vi.stubGlobal("ResizeObserver", class {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
        });
    });

    beforeEach(() => {
        vi.resetAllMocks();
        vi.mocked(issueInformationRequestStepUpChallenge).mockResolvedValue(undefined);
        vi.mocked(verifyInformationRequestStepUp).mockResolvedValue({
            sessionId: "session-a",
            verifiedUntil: Date.parse("2026-10-05T18:30:00Z")
        });
        vi.mocked(requestStepUp).mockResolvedValue(true);
    });

    afterEach(cleanup);

    it("stays out of the way when no protected question is waiting", () => {
        const {container} = render(<StepUpPanel requestId={"request-a"}
                                                awaiting={0}
                                                accessLinkToken={"bootstrap"}
                                                onVerified={vi.fn()}/>);

        expect(container.textContent).toBe("");
    });

    it("lets an access-link respondent send a code, verify it, and reload the questions", async () => {
        const onVerified = vi.fn();
        render(<StepUpPanel requestId={"request-a"}
                            awaiting={2}
                            accessLinkToken={"bootstrap"}
                            onVerified={onVerified}/>);

        expect(screen.getByText("2 protected questions are hidden until you verify again.")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Send verification code"}));
        expect(await screen.findByText("A verification code has been sent.")).toBeTruthy();
        fireEvent.change(screen.getByRole("textbox", {name: "Verification code"}), {target: {value: "123456"}});
        fireEvent.click(screen.getByRole("button", {name: "Verify"}));

        await waitFor(() => expect(onVerified).toHaveBeenCalledTimes(1));
        expect(issueInformationRequestStepUpChallenge).toHaveBeenCalledWith("request-a", "bootstrap");
        expect(verifyInformationRequestStepUp).toHaveBeenCalledWith("request-a", "bootstrap", "123456");
        expect(unnamedControls(document.body)).toEqual([]);
        expect(duplicateIds(document.body)).toEqual([]);
    });

    it("hands an ended access session back to the page, and no other refusal", async () => {
        vi.mocked(verifyInformationRequestStepUp).mockRejectedValue({
            errorMessage: "Invalid verification code",
            reasonCode: "INFORMATION_REQUEST_CONTACT_PROOF_INVALID",
        });
        const ended = {
            errorMessage: "Your verified session has ended.",
            reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED",
        };
        vi.mocked(issueInformationRequestStepUpChallenge).mockRejectedValue(ended);
        const onAccessSessionEnded = vi.fn();
        render(<StepUpPanel requestId={"request-a"}
                            awaiting={1}
                            accessLinkToken={"bootstrap"}
                            onVerified={vi.fn()}
                            onAccessSessionEnded={onAccessSessionEnded}/>);

        fireEvent.change(screen.getByRole("textbox", {name: "Verification code"}), {target: {value: "000000"}});
        fireEvent.click(screen.getByRole("button", {name: "Verify"}));
        expect(await screen.findByText("Invalid verification code")).toBeTruthy();
        expect(onAccessSessionEnded).not.toHaveBeenCalled();

        fireEvent.click(screen.getByRole("button", {name: "Send verification code"}));

        await waitFor(() => expect(onAccessSessionEnded).toHaveBeenCalledWith(ended));
    });

    it("explains a refused code and keeps the questions hidden", async () => {
        vi.mocked(verifyInformationRequestStepUp).mockRejectedValue({
            errorMessage: "Invalid verification code",
            reasonCode: "INFORMATION_REQUEST_CONTACT_PROOF_INVALID",
        });
        const onVerified = vi.fn();
        render(<StepUpPanel requestId={"request-a"}
                            awaiting={1}
                            accessLinkToken={"bootstrap"}
                            onVerified={onVerified}/>);

        expect(screen.getByText("1 protected question is hidden until you verify again.")).toBeTruthy();
        fireEvent.change(screen.getByRole("textbox", {name: "Verification code"}), {target: {value: "000000"}});
        fireEvent.click(screen.getByRole("button", {name: "Verify"}));

        expect(await screen.findByText("Invalid verification code")).toBeTruthy();
        expect(onVerified).not.toHaveBeenCalled();
    });

    it("announces a refused code to assistive technology", async () => {
        vi.mocked(verifyInformationRequestStepUp).mockRejectedValue({
            errorMessage: "Invalid verification code",
            reasonCode: "INFORMATION_REQUEST_CONTACT_PROOF_INVALID",
        });
        render(<StepUpPanel requestId={"request-a"}
                            awaiting={1}
                            accessLinkToken={"bootstrap"}
                            onVerified={vi.fn()}/>);

        fireEvent.change(screen.getByRole("textbox", {name: "Verification code"}), {target: {value: "000000"}});
        fireEvent.click(screen.getByRole("button", {name: "Verify"}));

        expect((await screen.findByRole("alert")).textContent).toBe("Invalid verification code");
    });

    it("lets a signed-in respondent verify again through their account", async () => {
        const onVerified = vi.fn();
        render(<StepUpPanel requestId={"request-a"}
                            awaiting={1}
                            onVerified={onVerified}/>);

        expect(screen.queryByRole("textbox", {name: "Verification code"})).toBeNull();
        fireEvent.click(screen.getByRole("button", {name: "Verify again"}));

        await waitFor(() => expect(onVerified).toHaveBeenCalledTimes(1));
        expect(requestStepUp).toHaveBeenCalledWith(expect.objectContaining({action: "see protected questions"}));
        expect(issueInformationRequestStepUpChallenge).not.toHaveBeenCalled();
    });

    it("keeps the questions hidden when a signed-in verification does not finish", async () => {
        vi.mocked(requestStepUp).mockRejectedValue(new Error("Step-up cancelled by user"));
        const onVerified = vi.fn();
        render(<StepUpPanel requestId={"request-a"}
                            awaiting={1}
                            onVerified={onVerified}/>);

        fireEvent.click(screen.getByRole("button", {name: "Verify again"}));

        expect(await screen.findByText("Verification did not finish.")).toBeTruthy();
        expect(onVerified).not.toHaveBeenCalled();
    });

    it("says until when protected questions stay open after verifying", async () => {
        const {rerender} = render(<StepUpPanel requestId={"request-a"}
                                               awaiting={1}
                                               accessLinkToken={"bootstrap"}
                                               onVerified={vi.fn()}/>);
        fireEvent.change(screen.getByRole("textbox", {name: "Verification code"}), {target: {value: "123456"}});
        fireEvent.click(screen.getByRole("button", {name: "Verify"}));
        await waitFor(() => expect(verifyInformationRequestStepUp).toHaveBeenCalled());

        rerender(<StepUpPanel requestId={"request-a"}
                              awaiting={0}
                              accessLinkToken={"bootstrap"}
                              onVerified={vi.fn()}/>);

        expect(await screen.findByText(
            `Protected questions stay open until ${formatInformationRequestTime(Date.parse("2026-10-05T18:30:00Z"))
                .replace(/\s+/g, " ")}. After that, you will be asked to verify again.`,
        )).toBeTruthy();
    });
});
