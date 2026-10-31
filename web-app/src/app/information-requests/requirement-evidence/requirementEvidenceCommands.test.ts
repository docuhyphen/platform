// @vitest-environment jsdom
import {beforeEach, describe, expect, it, vi} from "vitest";
import {
    listInformationRequestEvidence,
    openInformationRequestEvidenceContent,
    replaceInformationRequestEvidence,
    uploadInformationRequestEvidence,
    withdrawInformationRequestEvidence,
} from "../../../services/informationRequestEvidenceService.ts";
import {RequirementEvidenceCommands, requirementEvidenceCommands} from "./requirementEvidenceCommands.ts";

vi.mock("../../../services/informationRequestEvidenceService.ts", () => ({
    listInformationRequestEvidence: vi.fn(),
    uploadInformationRequestEvidence: vi.fn(),
    replaceInformationRequestEvidence: vi.fn(),
    withdrawInformationRequestEvidence: vi.fn(),
    openInformationRequestEvidenceContent: vi.fn(),
}));

const ended = {
    reasonCode: "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED",
    errorMessage: "Your verified session has ended."
};
const refused = {reasonCode: "INFORMATION_REQUEST_FORBIDDEN", errorMessage: "Access denied"};
const file = new File(["record"], "record.pdf", {type: "application/pdf"});

const operations = (commands: RequirementEvidenceCommands): Array<() => Promise<unknown>> => [
    () => commands.list("request-a", "requirement-a"),
    () => commands.upload("request-a", "requirement-a", file, "\"requirement-a:1\"", vi.fn(), "upload-key", {}),
    () => commands.replace("request-a", "requirement-a", "artifact-a", file, "\"artifact-a:1\"", vi.fn(), "replace-key", {}),
    () => commands.withdraw("request-a", "requirement-a", "artifact-a", "Sent the wrong file", "\"artifact-a:1\""),
    () => commands.open("request-a", "requirement-a", "artifact-a", "version-a", "content"),
];

const refuseEveryOperation = (refusal: unknown) => {
    vi.mocked(listInformationRequestEvidence).mockRejectedValue(refusal);
    vi.mocked(uploadInformationRequestEvidence).mockRejectedValue(refusal);
    vi.mocked(replaceInformationRequestEvidence).mockRejectedValue(refusal);
    vi.mocked(withdrawInformationRequestEvidence).mockRejectedValue(refusal);
    vi.mocked(openInformationRequestEvidenceContent).mockRejectedValue(refusal);
};

describe("requirementEvidenceCommands", () => {
    beforeEach(() => vi.resetAllMocks());

    it("reports an ended access session from every evidence operation and still refuses the operation", async () => {
        refuseEveryOperation(ended);
        const onAccessSessionEnded = vi.fn();
        const commands = requirementEvidenceCommands({accessLinkToken: "bootstrap-a", onAccessSessionEnded});

        for (const operation of operations(commands)) {
            await expect(operation()).rejects.toBe(ended);
        }

        expect(onAccessSessionEnded).toHaveBeenCalledTimes(5);
        expect(onAccessSessionEnded.mock.calls.every(([refusal]) => refusal === ended)).toBe(true);
    });

    it("leaves every other refusal to the operation that met it", async () => {
        refuseEveryOperation(refused);
        const onAccessSessionEnded = vi.fn();
        const commands = requirementEvidenceCommands({accessLinkToken: "bootstrap-a", onAccessSessionEnded});

        for (const operation of operations(commands)) {
            await expect(operation()).rejects.toBe(refused);
        }

        expect(onAccessSessionEnded).not.toHaveBeenCalled();
    });

    const interrupted = {isAxiosError: true, message: "Network Error"};
    const withdrawn = {outcome: "SAVED", etag: "\"artifact-a:2\"", data: {}};
    const withdrawalKeys = () => vi.mocked(withdrawInformationRequestEvidence).mock.calls.map(call => call[4].idempotencyKey);
    const withdraw = (commands: RequirementEvidenceCommands, reason = "Sent the wrong file") =>
        commands.withdraw("request-a", "requirement-a", "artifact-a", reason, "\"artifact-a:1\"");
    const minted = () => {
        let next = 0;
        return () => `key-${++next}`;
    };

    it("resends an interrupted withdrawal under the key its first attempt used, then keys the next one afresh", async () => {
        vi.mocked(withdrawInformationRequestEvidence)
            .mockRejectedValueOnce(interrupted)
            .mockResolvedValueOnce(withdrawn as never)
            .mockResolvedValueOnce(withdrawn as never);
        const commands = requirementEvidenceCommands({accessLinkToken: "bootstrap-a", newIdempotencyKey: minted()});

        await expect(withdraw(commands)).rejects.toBe(interrupted);
        await withdraw(commands);
        await withdraw(commands);

        const [first, retried, next] = withdrawalKeys();
        expect(retried).toBe(first);
        expect(next).not.toBe(first);
    });

    it("keys a withdrawal with another reason afresh while the earlier one is unanswered", async () => {
        vi.mocked(withdrawInformationRequestEvidence).mockRejectedValueOnce(interrupted).mockResolvedValueOnce(withdrawn as never);
        const commands = requirementEvidenceCommands({newIdempotencyKey: minted()});

        await expect(withdraw(commands)).rejects.toBe(interrupted);
        await withdraw(commands, "Replaced by a later file");

        const [first, changed] = withdrawalKeys();
        expect(changed).not.toBe(first);
    });

    it("resends an interrupted withdrawal under its key from commands made again with the same command keys", async () => {
        vi.mocked(withdrawInformationRequestEvidence).mockRejectedValueOnce(interrupted).mockResolvedValueOnce(withdrawn as never);
        const commandKeys = new Map<string, string>();
        const mint = minted();

        await expect(withdraw(requirementEvidenceCommands({
            commandKeys,
            newIdempotencyKey: mint
        }))).rejects.toBe(interrupted);
        await withdraw(requirementEvidenceCommands({commandKeys, newIdempotencyKey: mint}));

        const [first, retried] = withdrawalKeys();
        expect(retried).toBe(first);
    });
});
