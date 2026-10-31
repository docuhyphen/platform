/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import {
    InformationRequestPrivacyRequestDto,
    InformationRequestPrivacyRequestKind,
    InformationRequestPrivacyRequestState,
    InformationRequestPrivacyTargetOutcome,
    InformationRequestRecordExportDto,
    InformationRequestSubjectKind,
} from "../../../models/models.tsx";
import PrivacyPanel from "./PrivacyPanel.tsx";
import {duplicateIds} from "../../shared/testing/duplicateIds.ts";
import {unnamedControls} from "../../shared/testing/unnamedControls.ts";

vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({getInformationRequestSubjects: vi.fn()}));
vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({
    getInformationRequestPrivacyRequests: vi.fn(),
    getInformationRequestPrivacyRecordExport: vi.fn(),
    recordInformationRequestPrivacyRequest: vi.fn(),
    getInformationRequestSubjectRestrictions: vi.fn(),
    liftInformationRequestSubjectRestriction: vi.fn(),
}));

const subject = {
    id: "subject-a",
    subjectKind: InformationRequestSubjectKind.PERSON,
    references: [{authority: "internal", identifierType: "member_number", identifierValue: "M-100"}],
    createdAt: Date.parse("2026-09-01T08:00:00Z"),
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
    recordedAt: Date.parse("2026-09-20T08:00:00Z"),
    completedAt: Date.parse("2026-09-20T08:01:00Z"),
    targets: [{requestId: "request-a", outcome: InformationRequestPrivacyTargetOutcome.EXPORTED}],
    ...overrides,
});

const liftWithRefusedReread = async () => {
    vi.mocked(administration.getInformationRequestPrivacyRequests).mockResolvedValue([privacyRequest({})]);
    vi.mocked(administration.getInformationRequestSubjectRestrictions).mockResolvedValue([{
        id: "restriction-a",
        subjectIdentityRefId: "subject-a",
        privacyRequestId: "privacy-b",
        restrictedAt: Date.parse("2026-09-19T08:00:00Z"),
    }]);
    render(<PrivacyPanel/>);
    fireEvent.click(await screen.findByRole("button", {name: "Lift the restriction on Person, member number M-100"}));
    const dialog = await screen.findByRole("dialog", {name: "Lift this restriction"});
    fireEvent.change(within(dialog).getByLabelText(/Reason code/), {target: {value: "resolved.by.subject"}});
    vi.mocked(authoring.getInformationRequestSubjects)
        .mockRejectedValue({reasonCode: "INFORMATION_REQUEST_FORBIDDEN", errorMessage: "Privacy access was removed"});
    fireEvent.click(within(dialog).getByRole("button", {name: "Lift restriction"}));
    await screen.findByText("Privacy access was removed");
};

describe("PrivacyPanel", () =>
{
    beforeEach(() =>
    {
        vi.stubGlobal("ResizeObserver", class
        {
            observe() {
            }

            unobserve() {
            }

            disconnect() {
            }
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
            restrictedAt: Date.parse("2026-09-19T08:00:00Z"),
            liftedAt: Date.parse("2026-09-21T08:00:00Z"),
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
        }, expect.any(String)));
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

        expect(duplicateIds(document.body)).toEqual([]);
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
        }, expect.any(String)));
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
            restrictedAt: Date.parse("2026-09-19T08:00:00Z"),
        }]);
        render(<PrivacyPanel/>);

        const history = await screen.findByRole("list", {name: "Privacy requests"});
        const exported = within(history).getAllByRole("listitem")[0];
        expect(within(exported).getByText("Export for Person, member number M-100")).toBeTruthy();
        expect(within(exported).getByText(/Completed/)).toBeTruthy();
        expect(within(exported).getByText("1 request exported")).toBeTruthy();

        fireEvent.click(screen.getByRole("button", {name: "Lift the restriction on Person, member number M-100"}));
        const dialog = await screen.findByRole("dialog", {name: "Lift this restriction"});
        fireEvent.change(within(dialog).getByLabelText(/Reason code/), {target: {value: "Resolved by subject"}});
        expect(within(dialog).getByText("Use lowercase letters, digits, dots, dashes, or underscores, starting with a letter or digit.")).toBeTruthy();
        expect((within(dialog).getByRole("button", {name: "Lift restriction"}) as HTMLButtonElement).disabled).toBe(true);
        fireEvent.change(within(dialog).getByLabelText(/Reason code/), {target: {value: " Resolved.by.subject "}});
        fireEvent.click(within(dialog).getByRole("button", {name: "Lift restriction"}));

        await waitFor(() => expect(administration.liftInformationRequestSubjectRestriction)
            .toHaveBeenCalledWith("restriction-a", "resolved.by.subject", expect.any(String)));
        expect(await screen.findByText("The restriction was lifted.")).toBeTruthy();
    });

    it("marks an invalid reason code as the field's validation error and keeps the example as its hint", async () => {
        vi.mocked(administration.getInformationRequestSubjectRestrictions).mockResolvedValue([{
            id: "restriction-a",
            subjectIdentityRefId: "subject-a",
            privacyRequestId: "privacy-b",
            restrictedAt: Date.parse("2026-09-19T08:00:00Z"),
        }]);
        render(<PrivacyPanel/>);
        fireEvent.click(await screen.findByRole("button", {name: "Lift the restriction on Person, member number M-100"}));
        const dialog = within(await screen.findByRole("dialog", {name: "Lift this restriction"}));
        const reason = dialog.getByLabelText(/Reason code/);

        fireEvent.change(reason, {target: {value: "Resolved by subject"}});

        expect(reason.getAttribute("aria-invalid")).toBe("true");
        expect(dialog.getByText("A short code recorded with the restriction, for example resolved.by.subject.")).toBeTruthy();
        expect(dialog.getByText("Use lowercase letters, digits, dots, dashes, or underscores, starting with a letter or digit.")).toBeTruthy();
    });

    it("records a privacy request again under the same Idempotency-Key after its answer was lost", async () => {
        vi.mocked(administration.recordInformationRequestPrivacyRequest)
            .mockRejectedValueOnce(new Error("Network Error"))
            .mockResolvedValueOnce(privacyRequest({}));
        render(<PrivacyPanel/>);
        fireEvent.click(await screen.findByRole("button", {name: "Record a privacy request for Person, member number M-100"}));
        const dialog = within(await screen.findByRole("dialog", {name: "Record a privacy request"}));
        fireEvent.change(dialog.getByLabelText(/Purpose/), {target: {value: "subject-request"}});
        fireEvent.change(dialog.getByLabelText(/Policy basis/), {target: {value: "consent"}});
        fireEvent.click(dialog.getByRole("button", {name: "Record request"}));
        await waitFor(() => expect(document.getElementById("information-request-record-privacy-refusal")).not.toBeNull());

        fireEvent.click(document.getElementById("information-request-record-privacy-dialog-confirm") as HTMLElement);

        await waitFor(() => expect(administration.recordInformationRequestPrivacyRequest).toHaveBeenCalledTimes(2));
        const [sent, resent] = vi.mocked(administration.recordInformationRequestPrivacyRequest).mock.calls.map(call => call[1]);
        expect(sent).toMatch(/\S/);
        expect(resent).toBe(sent);
    });

    it("lifts a restriction again under the same Idempotency-Key after its answer was lost", async () => {
        vi.mocked(administration.getInformationRequestSubjectRestrictions).mockResolvedValue([{
            id: "restriction-a",
            subjectIdentityRefId: "subject-a",
            privacyRequestId: "privacy-b",
            restrictedAt: Date.parse("2026-09-19T08:00:00Z"),
        }]);
        vi.mocked(administration.liftInformationRequestSubjectRestriction).mockRejectedValueOnce(new Error("Network Error"));
        render(<PrivacyPanel/>);
        fireEvent.click(await screen.findByRole("button", {name: "Lift the restriction on Person, member number M-100"}));
        const dialog = within(await screen.findByRole("dialog", {name: "Lift this restriction"}));
        fireEvent.change(dialog.getByLabelText(/Reason code/), {target: {value: "resolved.by.subject"}});
        fireEvent.click(dialog.getByRole("button", {name: "Lift restriction"}));
        await waitFor(() => expect(document.getElementById("information-request-lift-restriction-refusal")).not.toBeNull());

        fireEvent.click(document.getElementById("information-request-lift-restriction-dialog-confirm") as HTMLElement);

        await waitFor(() => expect(administration.liftInformationRequestSubjectRestriction).toHaveBeenCalledTimes(2));
        const [sent, resent] = vi.mocked(administration.liftInformationRequestSubjectRestriction).mock.calls.map(call => call[2]);
        expect(sent).toMatch(/\S/);
        expect(resent).toBe(sent);
    });

    it("states the fallback instead of an HTML error page when the privacy records cannot be read", async () => {
        vi.mocked(administration.getInformationRequestPrivacyRequests)
            .mockRejectedValue("<html><body><h1>502 Bad Gateway</h1></body></html>");
        render(<PrivacyPanel/>);

        expect((await screen.findByRole("alert")).textContent).toContain("The privacy records could not be loaded.");
        expect(screen.queryByText(/Bad Gateway/)).toBeNull();
    });

    it("says an export's record was disposed instead of offering a download that cannot work", async () => {
        vi.mocked(administration.getInformationRequestPrivacyRequests).mockResolvedValue([
            privacyRequest({recordExportId: "export-a", recordExportAvailable: false}),
        ]);
        render(<PrivacyPanel/>);

        expect(await screen.findByText("Its record was disposed with a request it covered.")).toBeTruthy();
        expect(screen.queryByRole("button", {name: "Download record"})).toBeNull();
    });

    it("downloads a completed export's subject record from the privacy request that produced it", async () => {
        const createObjectURL = vi.fn(() => "blob:subject-record");
        vi.stubGlobal("URL", Object.assign(URL, {createObjectURL, revokeObjectURL: vi.fn()}));
        const save = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
        vi.mocked(administration.getInformationRequestPrivacyRequests).mockResolvedValue([
            privacyRequest({recordExportId: "export-a", recordExportAvailable: true}),
            privacyRequest({
                id: "privacy-b",
                requestKind: InformationRequestPrivacyRequestKind.RESTRICTION,
                targets: []
            }),
        ]);
        vi.mocked(administration.getInformationRequestPrivacyRecordExport).mockResolvedValue({
            id: "export-a",
            exportKind: "SUBJECT_RECORD",
            subjectIdentityRefId: "subject-a",
            sourceRequestIds: ["request-a"],
            schemaVersion: 4,
            contentHashAlgorithm: "SHA_256",
            contentHash: "a".repeat(64),
            contentLength: 2,
            storageLocation: "primary",
            transferDecision: "NOT_REQUESTED",
            requestedByPrincipalKind: "USER",
            requestedByPrincipalId: "user-a",
            requestedAt: Date.parse("2026-09-20T08:01:00Z"),
            verified: true,
            containsConfidentialContent: false,
            withheldRelatedRequestCount: 0,
            content: {},
        });
        render(<PrivacyPanel/>);

        const history = await screen.findByRole("list", {name: "Privacy requests"});
        const [exported, restricted] = within(history).getAllByRole("listitem");
        expect(within(restricted).queryByRole("button", {name: "Download record"})).toBeNull();
        fireEvent.click(within(exported).getByRole("button", {name: "Download record"}));

        await waitFor(() => expect(administration.getInformationRequestPrivacyRecordExport).toHaveBeenCalledWith("privacy-a"));
        await waitFor(() => expect(save).toHaveBeenCalled());
        save.mockRestore();
        vi.unstubAllGlobals();
    });

    it("withholds subject references, histories, restrictions, and their actions once the reread after a lift is refused", async () => {
        await liftWithRefusedReread();

        expect(screen.queryByRole("list", {name: "Privacy requests"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Record a privacy request for Person, member number M-100"})).toBeNull();
        expect(screen.queryByRole("button", {name: "Lift the restriction on Person, member number M-100"})).toBeNull();
        expect(screen.queryByText(/M-100/)).toBeNull();
    });

    it("reads the privacy records again on Try again and shows no earlier record until they answer", async () => {
        await liftWithRefusedReread();
        let answer: (subjects: typeof subject[]) => void = () => undefined;
        vi.mocked(authoring.getInformationRequestSubjects).mockImplementation(() => new Promise(resolve => {
            answer = resolve;
        }));

        fireEvent.click(screen.getByRole("button", {name: "Try again"}));

        expect(screen.queryByText("Privacy access was removed")).toBeNull();
        expect(screen.queryByRole("list", {name: "Privacy requests"})).toBeNull();
        expect(document.getElementById("information-request-privacy-loading")).not.toBeNull();
        await act(async () => answer([subject]));
        expect(await screen.findByRole("button", {name: "Record a privacy request for Person, member number M-100"})).toBeTruthy();
    });

    describe("downloading a subject record", () => {
        let saved: { href: string; download: string }[];
        let answerRead: (record: InformationRequestRecordExportDto) => void;

        const subjectRecord: InformationRequestRecordExportDto = {
            id: "export-a",
            exportKind: "SUBJECT_RECORD",
            subjectIdentityRefId: "subject-a",
            sourceRequestIds: ["request-a"],
            schemaVersion: 4,
            contentHashAlgorithm: "SHA_256",
            contentHash: "a".repeat(64),
            contentLength: 2,
            storageLocation: "primary",
            transferDecision: "NOT_REQUESTED",
            requestedByPrincipalKind: "USER",
            requestedByPrincipalId: "user-a",
            requestedAt: Date.parse("2026-09-20T08:01:00Z"),
            verified: true,
            containsConfidentialContent: false,
            withheldRelatedRequestCount: 0,
            content: {subject: "subject-a"},
        };

        const holdRead = () => vi.mocked(administration.getInformationRequestPrivacyRecordExport).mockImplementation(() =>
            new Promise(answer => {
                answerRead = answer;
            }));

        const startDownload = async () => {
            const history = await screen.findByRole("list", {name: "Privacy requests"});
            fireEvent.click(within(history).getByRole("button", {name: "Download record"}));
            await waitFor(() => expect(administration.getInformationRequestPrivacyRecordExport).toHaveBeenCalledWith("privacy-a"));
        };

        const deliverRecord = () => act(async () => {
            answerRead(subjectRecord);
            await new Promise<void>(resolve => setTimeout(resolve, 0));
        });

        const expectNothingSaved = () => {
            expect.soft(URL.createObjectURL).not.toHaveBeenCalled();
            expect.soft(saved).toEqual([]);
        };

        beforeEach(() => {
            saved = [];
            Object.assign(URL, {createObjectURL: vi.fn(() => "blob:subject-record"), revokeObjectURL: vi.fn()});
            vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
                saved.push({href: this.href, download: this.download});
            });
            vi.mocked(administration.getInformationRequestPrivacyRequests).mockResolvedValue([privacyRequest({
                recordExportId: "export-a",
                recordExportAvailable: true
            })]);
            vi.mocked(administration.getInformationRequestSubjectRestrictions).mockResolvedValue([{
                id: "restriction-a",
                subjectIdentityRefId: "subject-a",
                privacyRequestId: "privacy-b",
                restrictedAt: Date.parse("2026-09-19T08:00:00Z"),
            }]);
        });

        afterEach(() => vi.restoreAllMocks());

        it("saves no subject record still being read once the privacy records can no longer be read", async () => {
            holdRead();
            render(<PrivacyPanel/>);
            await startDownload();

            fireEvent.click(screen.getByRole("button", {name: "Lift the restriction on Person, member number M-100"}));
            const dialog = await screen.findByRole("dialog", {name: "Lift this restriction"});
            fireEvent.change(within(dialog).getByLabelText(/Reason code/), {target: {value: "resolved.by.subject"}});
            vi.mocked(authoring.getInformationRequestSubjects)
                .mockRejectedValue({
                    reasonCode: "INFORMATION_REQUEST_FORBIDDEN",
                    errorMessage: "Privacy access was removed"
                });
            fireEvent.click(within(dialog).getByRole("button", {name: "Lift restriction"}));
            await screen.findByText("Privacy access was removed");
            expect(screen.queryByRole("list", {name: "Privacy requests"})).toBeNull();
            await deliverRecord();

            expectNothingSaved();
        });

        it("saves no subject record still being read once the privacy panel closes", async () => {
            holdRead();
            const panel = render(<PrivacyPanel/>);
            await startDownload();

            panel.unmount();
            await deliverRecord();

            expectNothingSaved();
        });

        it("saves no subject record whose content no longer matches its hash and says why", async () => {
            vi.mocked(administration.getInformationRequestPrivacyRecordExport).mockResolvedValue({
                ...subjectRecord,
                verified: false
            });
            render(<PrivacyPanel/>);
            await startDownload();

            expect(await screen.findByText("The record no longer matches its recorded hash and was not downloaded.")).toBeTruthy();
            expect(saved).toEqual([]);
        });
    });
});
