/** @vitest-environment jsdom */
import {act, cleanup, renderHook} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import * as administration from "../../../../services/informationRequestAdministrationService.ts";
import {InformationRequestRecordExportDto} from "../../../models/models.tsx";
import {usePrivacyRecordDownload} from "./usePrivacyRecordDownload.ts";

vi.mock("../../../../services/informationRequestAdministrationService.ts", () => ({getInformationRequestPrivacyRecordExport: vi.fn()}));

const subjectRecord: InformationRequestRecordExportDto = {
    id: "export-a",
    exportKind: "SUBJECT_RECORD",
    subjectIdentityRefId: "subject-a",
    sourceRequestIds: ["request-a"],
    schemaVersion: 4,
    contentHashAlgorithm: "SHA-256",
    contentHash: "a".repeat(64),
    contentLength: 2,
    storageLocation: "primary",
    transferDecision: "NOT_REQUESTED",
    requestedByPrincipalKind: "USER",
    requestedByPrincipalId: "user-a",
    requestedAt: Date.parse("2026-10-14T08:00:00Z"),
    verified: true,
    containsConfidentialContent: false,
    withheldRelatedRequestCount: 0,
    content: {subject: "subject-a"},
};

describe("usePrivacyRecordDownload", () => {
    let saved: { href: string; download: string }[];

    beforeEach(() => {
        vi.resetAllMocks();
        saved = [];
        Object.assign(URL, {createObjectURL: vi.fn(() => "blob:subject-record"), revokeObjectURL: vi.fn()});
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
            saved.push({href: this.href, download: this.download});
        });
    });

    afterEach(() => {
        cleanup();
        vi.restoreAllMocks();
    });

    it("saves the subject record of the privacy request once its hash verifies", async () => {
        vi.mocked(administration.getInformationRequestPrivacyRecordExport).mockResolvedValue(subjectRecord);
        const hook = renderHook(() => usePrivacyRecordDownload());

        await act(async () => hook.result.current.download("privacy-a"));

        expect(administration.getInformationRequestPrivacyRecordExport).toHaveBeenCalledWith("privacy-a");
        expect(saved).toEqual([{href: "blob:subject-record", download: "subject-record-export-a.json"}]);
        expect(hook.result.current.error).toBeNull();
        expect(hook.result.current.downloading).toBeNull();
    });

    it("saves no subject record whose content no longer matches its hash and says why", async () => {
        vi.mocked(administration.getInformationRequestPrivacyRecordExport).mockResolvedValue({
            ...subjectRecord,
            verified: false
        });
        const hook = renderHook(() => usePrivacyRecordDownload());

        await act(async () => hook.result.current.download("privacy-a"));

        expect(saved).toEqual([]);
        expect(hook.result.current.error).toBe("The record no longer matches its recorded hash and was not downloaded.");
    });

    it("saves no subject record that arrives after its privacy requests closed", async () => {
        let answer: (record: InformationRequestRecordExportDto) => void = () => undefined;
        vi.mocked(administration.getInformationRequestPrivacyRecordExport).mockImplementation(() =>
            new Promise(resolve => {
                answer = resolve;
            }));
        const hook = renderHook(() => usePrivacyRecordDownload());
        let downloading: Promise<void> = Promise.resolve();
        act(() => {
            downloading = hook.result.current.download("privacy-a");
        });

        hook.unmount();
        await act(async () => {
            answer(subjectRecord);
            await downloading;
        });

        expect(URL.createObjectURL).not.toHaveBeenCalled();
        expect(saved).toEqual([]);
    });
});
