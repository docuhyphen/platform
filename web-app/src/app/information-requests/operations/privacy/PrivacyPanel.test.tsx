/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import {
    InformationRequestPrivacyRequestDto,
    InformationRequestPrivacyRequestKind,
    InformationRequestPrivacyRequestState,
    InformationRequestPrivacyTargetOutcome,
    InformationRequestSubjectKind,
} from "../../../models/models.tsx";
import PrivacyPanel from "./PrivacyPanel.tsx";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({getInformationRequestSubjects: vi.fn()}));
vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({
    getInformationRequestPrivacyRequests: vi.fn(),
    recordInformationRequestPrivacyRequest: vi.fn(),
    getInformationRequestSubjectRestrictions: vi.fn(),
    liftInformationRequestSubjectRestriction: vi.fn(),
}));

const subject = {
    id: "subject-a",
    subjectKind: InformationRequestSubjectKind.PERSON,
    references: [{authority: "internal", identifierType: "member_number", identifierValue: "M-100"}],
    createdAt: "2026-09-01T08:00:00Z",
};

const privacyRequest = (overrides: Partial<InformationRequestPrivacyRequestDto>): InformationRequestPrivacyRequestDto => ({
    id: "privacy-a",
    subjectIdentityRefId: "subject-a",
    requestKind: InformationRequestPrivacyRequestKind.EXPORT,
    purposeKey: "subject-request",
    policyBasisKey: "consent",
    state: InformationRequestPrivacyRequestState.COMPLETED,
    recordedByPrincipalKind: "USER",
    recordedByPrincipalId: "user-a",
    recordedAt: "2026-09-20T08:00:00Z",
    completedAt: "2026-09-20T08:01:00Z",
    targets: [{requestId: "request-a", outcome: InformationRequestPrivacyTargetOutcome.EXPORTED}],
    ...overrides,
});

describe("PrivacyPanel", () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });

    beforeEach(() =>
    {
        vi.clearAllMocks();
        vi.mocked(authoring.getInformationRequestSubjects).mockResolvedValue([subject]);
        vi.mocked(administration.getInformationRequestPrivacyRequests).mockResolvedValue([]);
        vi.mocked(administration.getInformationRequestSubjectRestrictions).mockResolvedValue([]);
        vi.mocked(administration.recordInformationRequestPrivacyRequest).mockResolvedValue(privacyRequest({}));
        vi.mocked(administration.liftInformationRequestSubjectRestriction).mockResolvedValue({
            id: "restriction-a",
            subjectIdentityRefId: "subject-a",
            privacyRequestId: "privacy-b",
            restrictedAt: "2026-09-19T08:00:00Z",
            liftedAt: "2026-09-21T08:00:00Z",
            liftReasonCode: "resolved",
        });
    });

    afterEach(cleanup);

    it("names each subject by kind and reference and records an export request for one", async () =>
    {
        render(<PrivacyPanel/>);

        fireEvent.click(await screen.findByRole("button", {name: "Record a privacy request for Person, member number M-100"}));
        const dialog = await screen.findByRole("dialog", {name: "Record a privacy request"});
        fireEvent.change(within(dialog).getByLabelText(/Request/), {target: {value: InformationRequestPrivacyRequestKind.EXPORT}});
        fireEvent.change(within(dialog).getByLabelText(/Purpose/), {target: {value: "1-request"}});
        expect(within(dialog).getByText("Use lowercase letters, digits, dots, dashes, or underscores, starting with a letter.")).toBeTruthy();
        fireEvent.change(within(dialog).getByLabelText(/Purpose/), {target: {value: "subject-request"}});
        fireEvent.change(within(dialog).getByLabelText(/Policy basis/), {target: {value: "consent"}});
        fireEvent.change(within(dialog).getByLabelText(/Transfer region/), {target: {value: "EU"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Record request"}));

        await waitFor(() => expect(administration.recordInformationRequestPrivacyRequest).toHaveBeenCalledWith({
            subjectIdentityRefId: "subject-a",
            requestKind: InformationRequestPrivacyRequestKind.EXPORT,
            purposeKey: "subject-request",
            policyBasisKey: "consent",
            transferRegion: "EU",
        }));
        expect(await screen.findByText("The privacy request was recorded and completed.")).toBeTruthy();
        expect(administration.getInformationRequestPrivacyRequests).toHaveBeenCalledTimes(2);
    });

    it("names every control and region of the privacy records and their form", async () =>
    {
        render(<PrivacyPanel/>);
        fireEvent.click(await screen.findByRole("button", {name: "Record a privacy request for Person, member number M-100"}));
        const dialog = await screen.findByRole("dialog", {name: "Record a privacy request"});
        fireEvent.change(within(dialog).getByLabelText(/Request/), {target: {value: InformationRequestPrivacyRequestKind.EXPORT}});

        expect(unnamedControls(document.body)).toEqual([]);
    });

    it("warns before a deletion and states a refused deletion with its reason", async () =>
    {
        vi.mocked(administration.recordInformationRequestPrivacyRequest).mockResolvedValue(privacyRequest({
            requestKind: InformationRequestPrivacyRequestKind.DELETION,
            state: InformationRequestPrivacyRequestState.REFUSED,
            refusalCode: "RECORD_PRESERVATION_HOLD_ACTIVE",
            refusalDetail: "A preservation hold keeps a request",
            targets: [{requestId: "request-a", outcome: InformationRequestPrivacyTargetOutcome.REFUSED}],
        }));
        render(<PrivacyPanel/>);

        fireEvent.click(await screen.findByRole("button", {name: "Record a privacy request for Person, member number M-100"}));
        const dialog = await screen.findByRole("dialog", {name: "Record a privacy request"});
        fireEvent.change(within(dialog).getByLabelText(/Request/), {target: {value: InformationRequestPrivacyRequestKind.DELETION}});
        expect(within(dialog).getByText(/This cannot be undone/)).toBeTruthy();
        expect(within(dialog).queryByLabelText(/Transfer region/)).toBeNull();
        fireEvent.change(within(dialog).getByLabelText(/Purpose/), {target: {value: "subject-request"}});
        fireEvent.change(within(dialog).getByLabelText(/Policy basis/), {target: {value: "erasure"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Delete records"}));

        await waitFor(() => expect(administration.recordInformationRequestPrivacyRequest).toHaveBeenCalledWith({
            subjectIdentityRefId: "subject-a",
            requestKind: InformationRequestPrivacyRequestKind.DELETION,
            purposeKey: "subject-request",
            policyBasisKey: "erasure",
        }));
        expect(await screen.findByText("The privacy request was refused: A preservation hold keeps a request.")).toBeTruthy();
    });

    it("lists privacy requests with their outcomes and lifts an active restriction with a reason", async () =>
    {
        vi.mocked(administration.getInformationRequestPrivacyRequests).mockResolvedValue([
            privacyRequest({}),
            privacyRequest({
                id: "privacy-b",
                requestKind: InformationRequestPrivacyRequestKind.RESTRICTION,
                targets: [],
            }),
        ]);
        vi.mocked(administration.getInformationRequestSubjectRestrictions).mockResolvedValue([{
            id: "restriction-a",
            subjectIdentityRefId: "subject-a",
            privacyRequestId: "privacy-b",
            restrictedAt: "2026-09-19T08:00:00Z",
        }]);
        render(<PrivacyPanel/>);

        const history = await screen.findByRole("list", {name: "Privacy requests"});
        const exported = within(history).getAllByRole("listitem")[0];
        expect(within(exported).getByText("Export for Person, member number M-100")).toBeTruthy();
        expect(within(exported).getByText(/Completed/)).toBeTruthy();
        expect(within(exported).getByText("1 request exported")).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Lift the restriction on Person, member number M-100"}));
        const dialog = await screen.findByRole("dialog", {name: "Lift this restriction"});
        fireEvent.change(within(dialog).getByLabelText(/Reason/), {target: {value: "resolved"}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Lift restriction"}));

        await waitFor(() => expect(administration.liftInformationRequestSubjectRestriction).toHaveBeenCalledWith("restriction-a", "resolved"));
        expect(await screen.findByText("The restriction was lifted.")).toBeTruthy();
    });
});
