/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {InformationRequestSubmissionPackageDto} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import SubmissionPackageList from "./SubmissionPackageList.tsx";

const shownTime = (value: string): string => formatInformationRequestTime(value).replace(/\s+/g, " ");

const submission: InformationRequestSubmissionPackageDto = {
    id: "package-a",
    informationRequestId: "request-a",
    packageNumber: 1,
    templateVersionId: "version-a",
    contentHash: "a",
    manifestHash: "b",
    reviewRequired: false,
    completesRequest: true,
    submittedAt: "2026-09-20T10:00:00Z",
    submittedByCaller: true,
    withdrawn: false,
    items: [],
    attestations: [],
    supportingEvidenceLinks: [],
    undisclosedItemCount: 0,
};

describe("SubmissionPackageList", () =>
{
    afterEach(cleanup);

    it("states when each package was submitted with the viewer's time zone and withdraws the caller's own", () =>
    {
        const withdraw = vi.fn();
        render(<SubmissionPackageList packages={[submission]}
                                      busy={false}
                                      closed={false}
                                      onWithdraw={withdraw}/>);

        expect(screen.getByText(shownTime("2026-09-20T10:00:00Z"))).toBeTruthy();
        fireEvent.click(screen.getByRole("button", {name: "Withdraw"}));
        expect(withdraw).toHaveBeenCalledWith("package-a");
    });
});
