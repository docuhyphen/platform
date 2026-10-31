/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as outcomes from "../../../../services/informationRequestOutcomeService.ts";
import * as submissions from "../../../../services/informationRequestSubmissionService.ts";
import {InformationRequestSubmissionPackageDto} from "../../../models/models.tsx";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";
import SubmissionRecoveryPanel from "./SubmissionRecoveryPanel.tsx";

vi.mock("../../../../services/informationRequestOutcomeService.ts", () => ({getInformationRequestSubmissionPackages: vi.fn()}));
vi.mock("../../../../services/informationRequestSubmissionService.ts", () => ({withdrawInformationRequestPackage: vi.fn()}));

const submission = (overrides: Partial<InformationRequestSubmissionPackageDto>): InformationRequestSubmissionPackageDto => ({
    id: "package-a",
    informationRequestId: "request-a",
    packageNumber: 1,
    stageKey: "first-part",
    templateVersionId: "version-a",
    contentHash: "content-a",
    manifestHash: "manifest-a",
    reviewRequired: false,
    completesRequest: false,
    submittedAt: Date.parse("2026-09-28T08:00:00Z"),
    submittedByCaller: false,
    withdrawn: false,
    items: [],
    attestations: [],
    supportingEvidenceLinks: [],
    undisclosedItemCount: 0,
    current: true,
    withdrawable: true,
    ...overrides,
});

const packages = [
    submission({}),
    submission({id: "package-b", packageNumber: 2, stageKey: "second-part", current: false, withdrawable: false}),
    submission({
        id: "package-c",
        packageNumber: 3,
        stageKey: "second-part",
        previousPackageId: "package-b",
        withdrawable: false,
        withdrawn: true,
        withdrawnAt: Date.parse("2026-09-29T08:00:00Z"),
    }),
];

const renderPanel = (onChanged = vi.fn()) => {
    render(
        <SubmissionRecoveryPanel requestId={"request-a"}
                                 responseETag={"\"responses-1\""}
                                 onChanged={onChanged}/>,
    );
    return onChanged;
};

describe("SubmissionRecoveryPanel", () => {
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
        vi.clearAllMocks();
        vi.mocked(outcomes.getInformationRequestSubmissionPackages).mockResolvedValue(packages);
        vi.mocked(submissions.withdrawInformationRequestPackage)
            .mockResolvedValue({outcome: "SAVED", responseETag: "\"responses-2\"", data: {} as never});
    });

    afterEach(cleanup);

    it("lists each submission with its standing and withdraws a withdrawable one under the response ETag", async () => {
        const onChanged = renderPanel();

        const panel = await screen.findByRole("region", {name: "Submissions"});
        expect(within(panel).getByText("Submission 1 - first part")).toBeTruthy();
        expect(within(panel).getByText("Submission 2 - second part")).toBeTruthy();
        expect(within(panel).getByText("Submission 3 - second part")).toBeTruthy();
        expect(within(panel).getAllByText("Submitted").length).toBe(1);
        expect(within(panel).getByText("Replaced")).toBeTruthy();
        expect(within(panel).getByText("Withdrawn")).toBeTruthy();
        expect(within(panel).getAllByRole("button", {name: /^Withdraw submission/}).map(button => button.textContent)).toEqual(["Withdraw"]);
        expect(unnamedControls(document.body)).toEqual([]);
        expect(duplicateIds(document.body)).toEqual([]);

        fireEvent.click(within(panel).getByRole("button", {name: "Withdraw submission 1"}));
        const dialog = await screen.findByRole("dialog", {name: "Withdraw submission 1"});
        fireEvent.change(within(dialog).getByLabelText("Reason code"), {target: {value: "recovery"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Withdraw submission"}));

        await waitFor(() => expect(submissions.withdrawInformationRequestPackage).toHaveBeenCalledWith(
            "request-a",
            "package-a",
            {reasonCode: "recovery"},
            {expectedETag: "\"responses-1\"", idempotencyKey: expect.any(String)},
        ));
        expect(await screen.findByText(/The submission was withdrawn/)).toBeTruthy();
        await waitFor(() => expect(screen.queryByRole("dialog", {name: "Withdraw submission 1"})).toBeNull());
        expect(onChanged).toHaveBeenCalledTimes(1);
        expect(outcomes.getInformationRequestSubmissionPackages).toHaveBeenCalledTimes(2);
    });

    it("keeps a refused withdrawal open with its reason and an inline error", async () => {
        vi.mocked(submissions.withdrawInformationRequestPackage).mockRejectedValue({errorMessage: "Withdraw the later part first"});
        const onChanged = renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "Withdraw submission 1"}));
        const dialog = await screen.findByRole("dialog", {name: "Withdraw submission 1"});
        fireEvent.change(within(dialog).getByLabelText("Reason code"), {target: {value: "recovery"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Withdraw submission"}));

        expect(await screen.findByText("Withdraw the later part first")).toBeTruthy();
        const kept = screen.getByRole("dialog", {name: "Withdraw submission 1"});
        expect(within(kept).getByText("Withdraw the later part first")).toBeTruthy();
        expect((within(kept).getByLabelText("Reason code") as HTMLInputElement).value).toBe("recovery");
        expect(onChanged).not.toHaveBeenCalled();
    });

    it("says that the request changed when the withdrawal was stale and reads the submissions again", async () => {
        vi.mocked(submissions.withdrawInformationRequestPackage).mockResolvedValue({outcome: "STALE"});
        const onChanged = renderPanel();

        fireEvent.click(await screen.findByRole("button", {name: "Withdraw submission 1"}));
        const dialog = await screen.findByRole("dialog", {name: "Withdraw submission 1"});
        fireEvent.click(within(dialog).getByRole("button", {name: "Withdraw submission"}));

        expect(await screen.findByText(/This request changed while you were working/)).toBeTruthy();
        await waitFor(() => expect(screen.queryByRole("dialog", {name: "Withdraw submission 1"})).toBeNull());
        expect(onChanged).toHaveBeenCalledTimes(1);
    });

    it("renders nothing while the request has no submissions", async () => {
        vi.mocked(outcomes.getInformationRequestSubmissionPackages).mockResolvedValue([]);
        renderPanel();

        await waitFor(() => expect(outcomes.getInformationRequestSubmissionPackages).toHaveBeenCalledTimes(1));
        expect(screen.queryByRole("region", {name: "Submissions"})).toBeNull();
    });

    it("states a failed read with Try again and lists the submissions once a retry loads", async () => {
        vi.mocked(outcomes.getInformationRequestSubmissionPackages)
            .mockRejectedValueOnce({errorMessage: "Submissions access refused"})
            .mockResolvedValueOnce(packages);
        renderPanel();

        expect(await screen.findByText("Submissions access refused")).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(await screen.findByText("Submission 1 - first part")).toBeTruthy();
        expect(screen.queryByText("Submissions access refused")).toBeNull();
    });
});
