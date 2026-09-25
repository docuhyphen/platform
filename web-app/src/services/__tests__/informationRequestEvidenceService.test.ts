// @vitest-environment jsdom
import {AxiosError, AxiosHeaders} from "axios";
import {beforeEach, describe, expect, it, vi} from "vitest";
import apiClient from "../apiClient.ts";
import {
    listInformationRequestEvidence,
    openInformationRequestEvidenceContent,
    replaceInformationRequestEvidence,
    uploadInformationRequestEvidence,
    withdrawInformationRequestEvidence,
} from "../informationRequestEvidenceService.ts";
import {storeInformationRequestSessionToken} from "../informationRequestRuntimeService.ts";

vi.mock("../apiClient.ts", () => ({default: {get: vi.fn(), post: vi.fn()}}));

const evidencePath = "/information-requests/request-a/requirements/requirement-a/evidence-artifacts";
const commandResult = {artifact: {id: "artifact-a"}, evidenceETag: "\"requirement-a:1\"", artifactETag: "\"artifact-a:1\""};

const refusal = (status: number, data: object) =>
{
    const error = new AxiosError("Refused", String(status));
    error.response = {status, data, statusText: "", headers: {}, config: {headers: new AxiosHeaders()}};
    return error;
};

describe("Information Request evidence transport", () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        window.sessionStorage.clear();
        vi.mocked(apiClient.get).mockResolvedValue({data: {requirementId: "requirement-a", evidenceETag: "\"requirement-a:0\""}});
        vi.mocked(apiClient.post).mockResolvedValue({data: commandResult});
    });

    it("lists a Requirement occurrence's evidence on both access surfaces", async () =>
    {
        const listed = await listInformationRequestEvidence("request-a", "requirement-a");
        expect(listed.evidenceETag).toBe("\"requirement-a:0\"");
        expect(apiClient.get).toHaveBeenLastCalledWith(evidencePath, {headers: undefined});

        storeInformationRequestSessionToken("request-a", "session-secret");
        await listInformationRequestEvidence("request-a", "requirement-a", "bootstrap");
        expect(apiClient.get).toHaveBeenLastCalledWith(`/no-auth${evidencePath}`, {
            headers: {"X-Request-Access-Token": "bootstrap", "X-Request-Session-Token": "session-secret"},
        });
    });

    it("uploads one file as multipart under the occurrence precondition and a fresh key", async () =>
    {
        const onProgress = vi.fn();
        const file = new File(["record"], "record.pdf", {type: "application/pdf"});

        const outcome = await uploadInformationRequestEvidence("request-a", "requirement-a", file, {
            expectedETag: "\"requirement-a:0\"",
            idempotencyKey: "upload-key",
            attributes: {issuer: "  Process Registry  ", language: " "},
            onProgress,
        });

        expect(outcome).toEqual({outcome: "SAVED", result: commandResult});
        const [path, form, config] = vi.mocked(apiClient.post).mock.calls[0];
        expect(path).toBe(evidencePath);
        const body = form as FormData;
        expect(body.get("file")).toBe(file);
        expect(body.get("encryptionMode")).toBe("INTERNAL");
        expect(body.get("issuer")).toBe("Process Registry");
        expect(body.has("language")).toBe(false);
        expect(config?.headers).toEqual(expect.objectContaining({
            "If-Match": "\"requirement-a:0\"",
            "Idempotency-Key": "upload-key",
            "Content-Type": "multipart/form-data",
        }));
        config?.onUploadProgress?.({loaded: 5, total: 10, bytes: 5, lengthComputable: true});
        expect(onProgress).toHaveBeenCalledWith(50);
    });

    it("replaces an artifact under its own revision and withdraws it with a reason", async () =>
    {
        const file = new File(["revised"], "revised.pdf");
        await replaceInformationRequestEvidence("request-a", "requirement-a", "artifact-a", file, {
            expectedETag: "\"artifact-a:1\"",
            idempotencyKey: "replace-key",
        });
        expect(vi.mocked(apiClient.post).mock.calls[0][0]).toBe(`${evidencePath}/artifact-a/versions`);
        expect(vi.mocked(apiClient.post).mock.calls[0][2]?.headers).toEqual(expect.objectContaining({"If-Match": "\"artifact-a:1\""}));

        await withdrawInformationRequestEvidence("request-a", "requirement-a", "artifact-a", "Recorded in error", {
            expectedETag: "\"artifact-a:2\"",
            idempotencyKey: "withdraw-key",
        });
        expect(apiClient.post).toHaveBeenLastCalledWith(
            `${evidencePath}/artifact-a/withdrawals`,
            {reason: "Recorded in error"},
            {headers: {"If-Match": "\"artifact-a:2\"", "Idempotency-Key": "withdraw-key"}},
        );
    });

    it("reports a stale precondition and passes any other refusal through", async () =>
    {
        vi.mocked(apiClient.post).mockRejectedValueOnce(refusal(412, {reasonCode: "COMMAND_PRECONDITION_STALE"}));
        const options = {expectedETag: "\"requirement-a:0\"", idempotencyKey: "key"};

        await expect(uploadInformationRequestEvidence("request-a", "requirement-a", new File(["a"], "a.pdf"), options))
            .resolves.toEqual({outcome: "STALE"});

        const duplicate = {reasonCode: "INFORMATION_REQUEST_EVIDENCE_DUPLICATE_CONTENT", errorMessage: "Already provided"};
        vi.mocked(apiClient.post).mockRejectedValueOnce(refusal(409, duplicate));
        await expect(uploadInformationRequestEvidence("request-a", "requirement-a", new File(["a"], "a.pdf"), options))
            .rejects.toEqual(duplicate);
    });

    it("reads version content as a blob for download or preview", async () =>
    {
        const blob = new Blob(["record"]);
        vi.mocked(apiClient.get).mockResolvedValueOnce({data: blob});

        const content = await openInformationRequestEvidenceContent("request-a", "requirement-a", "artifact-a", "version-a", "preview");

        expect(content).toBe(blob);
        expect(apiClient.get).toHaveBeenLastCalledWith(
            `${evidencePath}/artifact-a/versions/version-a/preview`,
            {headers: undefined, responseType: "blob"},
        );
    });
});
