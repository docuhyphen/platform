/** @vitest-environment jsdom */
import {act, cleanup, renderHook, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import * as transport from "../../../../services/informationRequestOperationsService.ts";
import {InformationRequestRecordExportDto} from "../../../models/models.tsx";
import {useRecordExports} from "./useRecordExports.ts";

vi.mock("../../../../services/informationRequestOperationsService.ts", () => ({
    createInformationRequestRecordExport: vi.fn(),
    getInformationRequestRecordExport: vi.fn(),
    getInformationRequestRecordExports: vi.fn(),
}));

const frozen: InformationRequestRecordExportDto = {
    id: "export-a",
    exportKind: "REQUEST_RECORD",
    requestId: "request-a",
    sourceRequestIds: ["request-a"],
    schemaVersion: 1,
    contentHashAlgorithm: "SHA-256",
    contentHash: "0".repeat(64),
    contentLength: 2,
    storageLocation: "primary",
    transferDecision: "NOT_REQUESTED",
    requestedByPrincipalKind: "USER",
    requestedByPrincipalId: "user-a",
    requestedAt: Date.parse("2026-10-12T06:00:00Z"),
    verified: true,
    containsConfidentialContent: false,
    withheldRelatedRequestCount: 0,
};

const sentKeys = (): string[] =>
    vi.mocked(transport.createInformationRequestRecordExport).mock.calls.map(call => call[1]);

const renderExports = async (requestId: string = "request-a") => {
    const hook = renderHook(({id}) => useRecordExports(id), {initialProps: {id: requestId}});
    await waitFor(() => expect(transport.getInformationRequestRecordExports).toHaveBeenCalled());
    return hook;
};

describe("useRecordExports", () => {
    beforeEach(() => {
        vi.resetAllMocks();
        vi.mocked(transport.getInformationRequestRecordExports).mockResolvedValue([]);
    });

    afterEach(cleanup);

    it("sends Create export again under the key of the attempt whose answer was lost, then lists the export", async () => {
        vi.mocked(transport.createInformationRequestRecordExport)
            .mockRejectedValueOnce({errorMessage: "The connection was lost"})
            .mockResolvedValueOnce(frozen);
        const hook = await renderExports();

        await act(async () => hook.result.current.create());
        expect(hook.result.current.error).toBe("The connection was lost");
        expect(transport.getInformationRequestRecordExports).toHaveBeenCalledTimes(1);
        await act(async () => hook.result.current.create());

        const [first, retried] = sentKeys();
        expect(first).toMatch(/\S/);
        expect(retried).toBe(first);
        expect(hook.result.current.error).toBeNull();
        await waitFor(() => expect(transport.getInformationRequestRecordExports).toHaveBeenCalledTimes(2));
    });

    it("makes a separate export under a new key once the previous one was answered", async () => {
        vi.mocked(transport.createInformationRequestRecordExport).mockResolvedValue(frozen);
        const hook = await renderExports();

        await act(async () => hook.result.current.create());
        await act(async () => hook.result.current.create());

        const [first, next] = sentKeys();
        expect(next).not.toBe(first);
    });

    it("keeps an unanswered export for one request apart from an export of another request", async () => {
        vi.mocked(transport.createInformationRequestRecordExport).mockRejectedValue({errorMessage: "The connection was lost"});
        const hook = await renderExports();

        await act(async () => hook.result.current.create());
        hook.rerender({id: "request-b"});
        await act(async () => hook.result.current.create());
        hook.rerender({id: "request-a"});
        await act(async () => hook.result.current.create());

        const [first, otherRequest, repeated] = sentKeys();
        expect(vi.mocked(transport.createInformationRequestRecordExport).mock.calls.map(call => call[0]))
            .toEqual(["request-a", "request-b", "request-a"]);
        expect(otherRequest).not.toBe(first);
        expect(repeated).toBe(first);
    });
});

describe("useRecordExports downloading a record", () => {
    let saved: { href: string; download: string }[];
    let pendingRead: (record: InformationRequestRecordExportDto) => void;
    let refusedRead: (refusal: unknown) => void;

    const withContent: InformationRequestRecordExportDto = {...frozen, content: {request: "request-a"}};

    const holdRead = () => vi.mocked(transport.getInformationRequestRecordExport).mockImplementation(() =>
        new Promise((answer, refuse) => {
            pendingRead = answer;
            refusedRead = refuse;
        }));

    beforeEach(() => {
        vi.resetAllMocks();
        saved = [];
        vi.mocked(transport.getInformationRequestRecordExports).mockResolvedValue([]);
        Object.assign(URL, {createObjectURL: vi.fn(() => "blob:request-record"), revokeObjectURL: vi.fn()});
        vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
            saved.push({href: this.href, download: this.download});
        });
    });

    afterEach(() => {
        cleanup();
        vi.restoreAllMocks();
    });

    it("saves the record of the request it shows once its hash verifies", async () => {
        vi.mocked(transport.getInformationRequestRecordExport).mockResolvedValue(withContent);
        const hook = await renderExports();

        await act(async () => hook.result.current.download("export-a"));

        expect(transport.getInformationRequestRecordExport).toHaveBeenCalledWith("request-a", "export-a");
        expect(saved).toEqual([{href: "blob:request-record", download: "information-request-record-export-a.json"}]);
        expect(hook.result.current.error).toBeNull();
    });

    it("saves no record whose content no longer matches its hash and says why", async () => {
        vi.mocked(transport.getInformationRequestRecordExport).mockResolvedValue({...withContent, verified: false});
        const hook = await renderExports();

        await act(async () => hook.result.current.download("export-a"));

        expect(saved).toEqual([]);
        expect(hook.result.current.error).toBe("The export no longer matches its recorded hash and was not downloaded.");
    });

    it("saves no record that arrives after its exports closed", async () => {
        holdRead();
        const hook = await renderExports();
        let downloading: Promise<void> = Promise.resolve();
        act(() => {
            downloading = hook.result.current.download("export-a");
        });

        hook.unmount();
        await act(async () => {
            pendingRead(withContent);
            await downloading;
        });

        expect(URL.createObjectURL).not.toHaveBeenCalled();
        expect(saved).toEqual([]);
    });

    it("saves no record of the earlier request once the exports show another request", async () => {
        holdRead();
        const hook = await renderExports();
        let downloading: Promise<void> = Promise.resolve();
        act(() => {
            downloading = hook.result.current.download("export-a");
        });

        hook.rerender({id: "request-b"});
        await act(async () => {
            pendingRead(withContent);
            await downloading;
        });

        expect(URL.createObjectURL).not.toHaveBeenCalled();
        expect(saved).toEqual([]);
        expect(hook.result.current.error).toBeNull();
    });

    it("says nothing about the earlier request's refused download once the exports show another request", async () => {
        holdRead();
        const hook = await renderExports();
        let downloading: Promise<void> = Promise.resolve();
        act(() => {
            downloading = hook.result.current.download("export-a");
        });

        hook.rerender({id: "request-b"});
        await act(async () => {
            refusedRead({errorMessage: "Reading the earlier export was refused"});
            await downloading;
        });

        expect(hook.result.current.error).toBeNull();
    });
});
