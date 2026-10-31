/** @vitest-environment jsdom */
import {act, cleanup, renderHook, RenderHookResult, waitFor} from "@testing-library/react";
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {
    InformationRequestEvidenceArtifactDto,
    InformationRequestEvidenceCollectionState,
    InformationRequestEvidenceSourceKind,
    InformationRequestEvidenceVersionDto,
} from "../../models/models.tsx";
import {InformationRequestEvidenceContentUse} from "../../../services/informationRequestEvidenceService.ts";
import {keptEvidenceUploads, KeptUpload} from "./keptEvidenceUploads.ts";
import {RequirementEvidenceCommands} from "./requirementEvidenceCommands.ts";
import {RequirementEvidence, useRequirementEvidence} from "./useRequirementEvidence.ts";
import {openingPreviewTab, PreviewTabDouble, previewTabDouble} from "./testing/previewTabDouble.ts";

const commands = {
    list: vi.fn(),
    upload: vi.fn(),
    replace: vi.fn(),
    withdraw: vi.fn(),
    open: vi.fn(),
};

const REFUSAL = {
    reasonCode: "INFORMATION_REQUEST_CONFIDENTIALITY_DENIED",
    errorMessage: "Verification is no longer current"
};

describe("useRequirementEvidence", () => {
    beforeEach(() => {
        vi.resetAllMocks();
        commands.list.mockRejectedValue(REFUSAL);
    });

    afterEach(cleanup);

    it("keeps a file sent while the evidence is withheld instead of sending or dropping it", async () => {
        const store = new Map<string, KeptUpload[]>();
        const hook = renderHook(() => useRequirementEvidence(
            "request-a",
            "requirement-a",
            commands as RequirementEvidenceCommands,
            keptEvidenceUploads(store, "respondent"),
        ));
        await waitFor(() => expect(hook.result.current.loadError).toBe("Verification is no longer current"));
        const file = new File(["record"], "record.pdf", {type: "application/pdf"});

        await act(async () => hook.result.current.upload(file, {}));

        expect(commands.upload).not.toHaveBeenCalled();
        expect(hook.result.current.kept.map(kept => kept.file)).toEqual([file]);
        expect(store.get("respondent|requirement-a")?.map(kept => kept.file)).toEqual([file]);
    });

    it("shows only the kept file of the Requirement it shows", async () => {
        const store = new Map<string, KeptUpload[]>();
        const earlier = new File(["earlier"], "earlier.pdf");
        const other = new File(["other"], "other.pdf");
        store.set("respondent|requirement-a", [{file: earlier, details: {}, idempotencyKey: "key-a"}]);
        store.set("respondent|requirement-b", [{file: other, details: {}, idempotencyKey: "key-b"}]);
        const hook = renderHook(({requirementId}) => useRequirementEvidence(
            "request-a",
            requirementId,
            commands as RequirementEvidenceCommands,
            keptEvidenceUploads(store, "respondent"),
        ), {initialProps: {requirementId: "requirement-a"}});
        expect(hook.result.current.kept.map(kept => kept.file)).toEqual([earlier]);
        act(() => hook.result.current.discard(hook.result.current.kept[0]));
        expect(hook.result.current.kept).toEqual([]);

        hook.rerender({requirementId: "requirement-b"});

        expect(hook.result.current.kept.map(kept => kept.file)).toEqual([other]);
        expect(store.has("respondent|requirement-a")).toBe(false);
    });

    describe("when a file opening answers late", () => {
        const content = new Blob(["record"], {type: "application/pdf"});
        const version: InformationRequestEvidenceVersionDto = {
            id: "version-a",
            versionNumber: 1,
            sourceKind: InformationRequestEvidenceSourceKind.DOCUMENT_VERSION,
            declaredFileName: "record.pdf",
            declaredMediaType: "application/pdf",
            createdByCaller: true,
            createdAt: Date.parse("2026-10-14T08:00:00Z"),
            findings: [],
        };
        const artifact: InformationRequestEvidenceArtifactDto = {
            id: "artifact-a",
            requirementId: "requirement-a",
            artifactKey: "record",
            collectionState: InformationRequestEvidenceCollectionState.ACTIVE,
            artifactRevision: 1,
            etag: "\"artifact-a:1\"",
            createdByCaller: true,
            changeableByCaller: true,
            createdAt: Date.parse("2026-10-14T08:00:00Z"),
            updatedAt: Date.parse("2026-10-14T08:00:00Z"),
            versions: [version],
        };
        let answer: () => void;
        let tab: PreviewTabDouble;

        beforeAll(() => {
            Object.assign(URL, {createObjectURL: vi.fn(), revokeObjectURL: vi.fn()});
        });

        beforeEach(() => {
            commands.list.mockResolvedValue({
                requirementId: "requirement-a",
                evidenceETag: "\"evidence:1\"",
                artifacts: [artifact]
            });
            commands.open.mockImplementation(() => new Promise<Blob>(resolve => {
                answer = () => resolve(content);
            }));
            vi.mocked(URL.createObjectURL).mockReturnValue("blob:record");
            tab = previewTabDouble();
            openingPreviewTab(tab);
            vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
        });

        afterEach(() => vi.restoreAllMocks());

        const renderEvidence = () => renderHook(({requirementId}) => useRequirementEvidence(
            "request-a",
            requirementId,
            commands as RequirementEvidenceCommands,
        ), {initialProps: {requirementId: "requirement-a"}});

        const startOpening = (evidence: RenderHookResult<RequirementEvidence, {
            requirementId: string
        }>, use: InformationRequestEvidenceContentUse) => {
            let opened: Promise<void> = Promise.resolve();
            act(() => {
                opened = evidence.result.current.open(artifact, version, use);
            });
            return opened;
        };

        it.each(["preview", "content"] as const)("opens nothing for a %s that answers after the evidence is gone", async use => {
            const hook = renderEvidence();
            const opened = startOpening(hook, use);

            hook.unmount();
            answer();
            await opened;

            expect(URL.createObjectURL).not.toHaveBeenCalled();
            expect(tab.location.replace).not.toHaveBeenCalled();
            expect(tab.close).toHaveBeenCalledTimes(use === "preview" ? 1 : 0);
            expect(HTMLAnchorElement.prototype.click).not.toHaveBeenCalled();
        });

        it("opens nothing for a file that answers after the evidence shows another Requirement", async () => {
            const hook = renderEvidence();
            const opened = startOpening(hook, "preview");

            hook.rerender({requirementId: "requirement-b"});
            answer();
            await act(async () => opened);

            expect(URL.createObjectURL).not.toHaveBeenCalled();
            expect(tab.location.replace).not.toHaveBeenCalled();
            expect(tab.close).toHaveBeenCalledTimes(1);
        });

        it("opens a file that answers while its Requirement is still shown", async () => {
            const hook = renderEvidence();
            await waitFor(() => expect(hook.result.current.evidence?.artifacts).toEqual([artifact]));
            const opened = startOpening(hook, "preview");

            answer();
            await act(async () => opened);

            expect(URL.createObjectURL).toHaveBeenCalledWith(content);
            expect(window.open).toHaveBeenCalledWith("", "_blank");
            expect(tab.location.replace).toHaveBeenCalledWith("blob:record");
        });
    });
});
