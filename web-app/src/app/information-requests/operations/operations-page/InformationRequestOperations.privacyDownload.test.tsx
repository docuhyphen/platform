/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor, within} from "@testing-library/react";
import {MemoryRouter, Route, Routes} from "react-router-dom";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import * as authoring from "../../../../services/informationRequestAuthoringService.ts";
import * as exchanges from "../../../../services/exchangeApi.ts";
import * as transport from "../../../../services/informationRequestOperationsService.ts";
import {
    InformationRequestPrivacyRequestDto,
    InformationRequestPrivacyRequestKind,
    InformationRequestPrivacyRequestState,
    InformationRequestPrivacyTargetOutcome,
    InformationRequestRecordExportDto,
    InformationRequestSubjectKind,
} from "../../../models/models.tsx";
import {useInformationRequestCapabilities} from "../../capabilities/useInformationRequestCapabilities.ts";
import {informationRequestCapabilities} from "../../shared/testing/capabilityFixtures.ts";
import InformationRequestOperations from "./InformationRequestOperations.tsx";

const mockAuth = vi.fn();

vi.mock("../../../../services/informationRequestOperationsService.ts", () => ({getInformationRequestOperations: vi.fn()}));
vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({
    sendInformationRequestReminders: vi.fn(),
    getInformationRequestPrivacyRequests: vi.fn(),
    getInformationRequestPrivacyRecordExport: vi.fn(),
    getInformationRequestSubjectRestrictions: vi.fn(),
    recordInformationRequestPrivacyRequest: vi.fn(),
    liftInformationRequestSubjectRestriction: vi.fn(),
}));
vi.mock("../../../../services/informationRequestAuthoringService.ts", () => ({getInformationRequestSubjects: vi.fn()}));
vi.mock("../../../../services/exchangeApi.ts", () => ({searchExchanges: vi.fn()}));
vi.mock("../../capabilities/useInformationRequestCapabilities.ts", () => ({useInformationRequestCapabilities: vi.fn()}));
vi.mock("../../../../context/AuthContext.tsx", () => ({useAuth: () => mockAuth()}));
vi.mock("../clock-policies/ClockPoliciesPanel.tsx", () => ({default: () => <p>Due date policies shown</p>}));
vi.mock("../audit-search/AuditSearchPanel.tsx", () => ({default: () => <p>Audit search results</p>}));

const exportRequest: InformationRequestPrivacyRequestDto = {
    id: "privacy-a",
    subjectIdentityRefId: "subject-a",
    requestKind: InformationRequestPrivacyRequestKind.EXPORT,
    purposeKey: "subject-request",
    policyBasisKey: "consent",
    state: InformationRequestPrivacyRequestState.COMPLETED,
    recordedByPrincipalKind: "USER",
    recordedByPrincipalId: "user-a",
    recordedAt: Date.parse("2026-10-14T08:00:00Z"),
    completedAt: Date.parse("2026-10-14T08:01:00Z"),
    recordExportId: "export-a",
    recordExportAvailable: true,
    targets: [{requestId: "request-a", outcome: InformationRequestPrivacyTargetOutcome.EXPORTED}],
};

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
    requestedAt: Date.parse("2026-10-14T08:01:00Z"),
    verified: true,
    containsConfidentialContent: false,
    withheldRelatedRequestCount: 0,
    content: {subject: "subject-a"},
};

const signedInTo = (organizationId: string) => mockAuth.mockReturnValue({
    appUser: {id: "user-a"},
    currentSession: {userId: "user-a", activeOrganizationId: organizationId},
    hasCapability: () => true,
});

const operationsPage = () => (
    <MemoryRouter initialEntries={["/information-request-operations"]}>
        <Routes>
            <Route path={"/information-request-operations"}
                   element={<InformationRequestOperations/>}/>
        </Routes>
    </MemoryRouter>
);

const settled = () => act(() => new Promise<void>(resolve => setTimeout(resolve, 0)));

describe("InformationRequestOperations privacy record downloads", () => {
    let saved: { href: string; download: string }[];
    let answerRead: (record: InformationRequestRecordExportDto) => void;

    const holdRead = () => vi.mocked(administration.getInformationRequestPrivacyRecordExport).mockImplementation(() =>
        new Promise(answer => {
            answerRead = answer;
        }));

    const startDownload = async () => {
        fireEvent.click(await screen.findByRole("tab", {name: "Privacy"}));
        const history = await screen.findByRole("list", {name: "Privacy requests"});
        fireEvent.click(within(history).getByRole("button", {name: "Download record"}));
        await waitFor(() => expect(administration.getInformationRequestPrivacyRecordExport).toHaveBeenCalledWith("privacy-a"));
    };

    const deliverRecord = async () => {
        await act(async () => answerRead(subjectRecord));
        await settled();
    };

    const expectNothingSaved = () => {
        expect.soft(URL.createObjectURL).not.toHaveBeenCalled();
        expect.soft(saved).toEqual([]);
    };

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
        saved = [];
        signedInTo("organization-a");
        Object.assign(URL, {createObjectURL: vi.fn(() => "blob:subject-record"), revokeObjectURL: vi.fn()});
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
            saved.push({href: this.href, download: this.download});
        });
        vi.mocked(useInformationRequestCapabilities).mockReturnValue(informationRequestCapabilities());
        vi.mocked(transport.getInformationRequestOperations).mockResolvedValue({
            items: [],
            total: 0,
            limit: 25,
            offset: 0
        });
        vi.mocked(exchanges.searchExchanges).mockResolvedValue({
            content: [],
            totalElements: 0,
            totalPages: 0,
            currentPage: 0,
            pageSize: 10
        });
        vi.mocked(authoring.getInformationRequestSubjects).mockResolvedValue([{
            id: "subject-a",
            subjectKind: InformationRequestSubjectKind.PERSON,
            references: [{authority: "internal", identifierType: "member_number", identifierValue: "M-100"}],
            createdAt: Date.parse("2026-10-01T08:00:00Z"),
        }]);
        vi.mocked(administration.getInformationRequestPrivacyRequests).mockResolvedValue([exportRequest]);
        vi.mocked(administration.getInformationRequestSubjectRestrictions).mockResolvedValue([]);
    });

    afterEach(() => {
        cleanup();
        vi.restoreAllMocks();
    });

    it("saves the verified subject record of the workspace on screen", async () => {
        vi.mocked(administration.getInformationRequestPrivacyRecordExport).mockResolvedValue(subjectRecord);
        render(operationsPage());

        await startDownload();

        await waitFor(() => expect(saved).toEqual([{
            href: "blob:subject-record",
            download: "subject-record-export-a.json"
        }]));
    });

    it("saves no subject record still being read once the workspace changes", async () => {
        holdRead();
        const page = render(operationsPage());
        await startDownload();

        signedInTo("organization-b");
        page.rerender(operationsPage());
        await waitFor(() => expect(administration.getInformationRequestPrivacyRequests).toHaveBeenCalledTimes(2));
        await deliverRecord();

        expectNothingSaved();
    });

    it("saves no subject record still being read once the queue is opened", async () => {
        holdRead();
        render(operationsPage());
        await startDownload();

        fireEvent.click(screen.getByRole("tab", {name: "Queue"}));
        await waitFor(() => expect(screen.queryByRole("list", {name: "Privacy requests"})).toBeNull());
        await deliverRecord();

        expectNothingSaved();
    });
});
