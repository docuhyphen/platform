/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, describe, expect, it, vi} from "vitest";
import {InformationRequestSubmissionPackageDto} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import SubmissionPackageList from "./SubmissionPackageList.tsx";

const shownTime = (value: number): string => formatInformationRequestTime(value).replace(/\s+/g, " ");

const submission: InformationRequestSubmissionPackageDto = {
    id: "package-a",
    informationRequestId: "request-a",
    packageNumber: 1,
    templateVersionId: "version-a",
    contentHash: "a",
    manifestHash: "b",
    reviewRequired: false,
    completesRequest: true,
    submittedAt: Date.parse("2026-09-20T10:00:00Z"),
    submittedByCaller: true,
    withdrawn: false,
    items: [],
    attestations: [],
    supportingEvidenceLinks: [],
    undisclosedItemCount: 0,
    current: true,
    withdrawable: true,
};

describe("SubmissionPackageList", () =>
{
    beforeAll(() => vi.stubGlobal("ResizeObserver", class {
        observe() {
        }

        unobserve() {
        }

        disconnect() {
        }
    }));
    afterEach(cleanup);

    it("states when each package was submitted with the viewer's time zone and withdraws the caller's own with confirmation and a reason", async () =>
    {
        const withdraw = vi.fn().mockResolvedValue(true);
        render(<SubmissionPackageList packages={[submission]}
                                      busy={false}
                                      closed={false}
                                      onWithdraw={withdraw}/>);

        expect(screen.getByText(shownTime(Date.parse("2026-09-20T10:00:00Z")))).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: /^Withdraw/}));
        expect(withdraw).not.toHaveBeenCalled();
        const dialog = screen.getByRole("dialog", {name: "Withdraw submission 1"});
        fireEvent.change(within(dialog).getByLabelText("Reason code"), {target: {value: "record.updated"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Withdraw submission"}));
        await waitFor(() => expect(withdraw).toHaveBeenCalledWith("package-a", "record.updated"));
    });

    it("offers recovery withdrawal for another party's package when permitted", () => {
        render(<SubmissionPackageList packages={[{...submission, submittedByCaller: false}]}
                                      busy={false}
                                      closed={false}
                                      onWithdraw={vi.fn()}/>);
        expect(screen.getByRole("button", {name: /^Withdraw/})).toBeTruthy();
    });
    it("offers no withdrawal of a package that answers a correction, yet withdraws one sent again after a withdrawal", () => {
        const corrected = {...submission, id: "package-a", current: false, withdrawable: false};
        const correction = {
            ...submission,
            id: "package-b",
            packageNumber: 2,
            previousPackageId: "package-a",
            withdrawable: false
        };
        const withdrawn = {...submission, id: "package-c", packageNumber: 3, withdrawn: true};
        const again = {...submission, id: "package-d", packageNumber: 4, previousPackageId: "package-c"};
        render(<SubmissionPackageList packages={[corrected, correction, withdrawn, again]}
                                      busy={false}
                                      closed={false}
                                      onWithdraw={vi.fn()}/>);

        expect(document.getElementById("information-request-submission-package-package-b-withdraw")).toBeNull();
        expect(document.getElementById("information-request-submission-package-package-b-correction")?.textContent)
            .toBe("Answers a correction, so it stays submitted.");
        expect(document.getElementById("information-request-submission-package-package-d-withdraw")).not.toBeNull();
        expect(document.getElementById("information-request-submission-package-package-d-correction")).toBeNull();
    });

    it("shows a package a later submission replaced as replaced and offers withdrawal only where the server allows it", () => {
        const replaced = {...submission, id: "package-a", current: false, withdrawable: false};
        const underReview = {...submission, id: "package-b", packageNumber: 2, withdrawable: false};
        const pending = {...submission, id: "package-c", packageNumber: 3};
        render(<SubmissionPackageList packages={[replaced, underReview, pending]}
                                      busy={false}
                                      closed={false}
                                      onWithdraw={vi.fn()}/>);

        expect(document.getElementById("information-request-submission-package-package-a-state")?.textContent).toBe("Replaced");
        expect(document.getElementById("information-request-submission-package-package-a-replaced")?.textContent)
            .toBe("Replaced by a later submission.");
        expect(document.getElementById("information-request-submission-package-package-a-withdraw")).toBeNull();
        expect(document.getElementById("information-request-submission-package-package-b-state")?.textContent).toBe("Submitted");
        expect(document.getElementById("information-request-submission-package-package-b-withdraw")).toBeNull();
        expect(document.getElementById("information-request-submission-package-package-c-withdraw")).not.toBeNull();
    });
});
