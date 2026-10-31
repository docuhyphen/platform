import {useEffect, useLayoutEffect, useRef, useState} from "react";
import {
    InformationRequestEvidenceArtifactDto,
    InformationRequestEvidenceListDto,
    InformationRequestEvidenceVersionDto,
} from "../../models/models.tsx";
import {
    InformationRequestEvidenceCommandOutcome,
    InformationRequestEvidenceContentUse,
} from "../../../services/informationRequestEvidenceService.ts";
import {usePendingWorkReporter} from "../shared/respondentPendingWork.ts";
import {Scoped, useScopeGuard, valueInScope} from "../shared/useScopeGuard.ts";
import {useServerRead} from "../shared/useServerRead.ts";
import {useStillMounted} from "../shared/useStillMounted.ts";
import {EvidenceDetails} from "./evidenceDetails.ts";
import {openEvidencePreviewTab, PREVIEW_BLOCKED_MESSAGE} from "./evidencePreviewTab.ts";
import {
    KeptEvidenceUploads,
    keptEvidenceUploads,
    KeptUpload,
    withKeptUpload,
    withoutKeptUpload,
} from "./keptEvidenceUploads.ts";
import {RequirementEvidenceCommands} from "./requirementEvidenceCommands.ts";
import {refusalMessage} from "./requirementEvidenceLabels.ts";

const STALE_MESSAGE = "This evidence changed while you were working. The latest files are shown, so try again.";
const LOAD_FAILURE = "The evidence for this Requirement could not be loaded.";
const OBJECT_URL_LIFETIME_MS = 60_000;

type EvidenceCommandResult = "SAVED" | "STALE" | "REFUSED";

const offersOpening = (
    evidence: InformationRequestEvidenceListDto | null,
    artifact: InformationRequestEvidenceArtifactDto,
    version: InformationRequestEvidenceVersionDto,
): boolean =>
    evidence?.artifacts.some(shown =>
        shown.id === artifact.id && shown.versions[shown.versions.length - 1]?.id === version.id) ?? false;

export interface RequirementEvidence
{
    evidence: InformationRequestEvidenceListDto | null;
    loadError: string | null;
    reload: () => Promise<void>;
    busy: boolean;
    progress: number | null;
    message: string | null;
    kept: KeptUpload[];
    retry: (upload: KeptUpload) => Promise<void>;
    discard: (upload: KeptUpload) => void;
    upload: (file: File, details: EvidenceDetails) => Promise<void>;
    replace: (artifact: InformationRequestEvidenceArtifactDto, file: File, details: EvidenceDetails) => Promise<void>;
    withdraw: (artifact: InformationRequestEvidenceArtifactDto, reason: string) => Promise<void>;
    open: (
        artifact: InformationRequestEvidenceArtifactDto,
        version: InformationRequestEvidenceVersionDto,
        use: InformationRequestEvidenceContentUse,
    ) => Promise<void>;
}

export const useRequirementEvidence = (
    requestId: string,
    requirementId: string,
    commands: RequirementEvidenceCommands,
    keptUploads?: KeptEvidenceUploads,
): RequirementEvidence =>
{
    const scope = `${requestId}|${requirementId}`;
    const guard = useScopeGuard(scope);
    const stillMounted = useStillMounted();
    const list = useServerRead(
        scope,
        () => commands.list(requestId, requirementId),
        refusal => refusalMessage(refusal, LOAD_FAILURE),
    );
    const evidence = list.value;
    const shownEvidence = useRef(evidence);
    const [busy, setBusy] = useState(false);
    const [progress, setProgress] = useState<number | null>(null);
    const [message, setMessage] = useState<Scoped<string> | null>(null);
    const [ownUploads] = useState(() => keptEvidenceUploads(new Map(), ""));
    const keptStore = keptUploads ?? ownUploads;
    const [, setKeptRevision] = useState(0);
    const kept = keptStore.kept(requirementId);
    const reporter = usePendingWorkReporter();
    const holdsUpload = kept.length > 0;

    useLayoutEffect(() => {
        shownEvidence.current = evidence;
    }, [evidence]);

    useEffect(() =>
    {
        reporter?.reportUpload(requirementId, {sending: busy, kept: holdsUpload});
    }, [busy, holdsUpload, reporter, requirementId]);

    useEffect(() => () => reporter?.reportUpload(requirementId, null), [reporter, requirementId]);

    const stillOffered = (
        artifact: InformationRequestEvidenceArtifactDto,
        version: InformationRequestEvidenceVersionDto,
    ): boolean => stillMounted() && guard.isCurrent(scope) && offersOpening(shownEvidence.current, artifact, version);

    const say = (text: string | null) => setMessage(text === null ? null : {scope, value: text});

    const keep = (change: (current: KeptUpload[]) => KeptUpload[]) => {
        keptStore.keep(requirementId, change(keptStore.kept(requirementId)));
        setKeptRevision(revision => revision + 1);
    };

    const run = async (
        command: () => Promise<InformationRequestEvidenceCommandOutcome>,
        fallback: string,
        tracksProgress: boolean,
    ): Promise<EvidenceCommandResult> =>
    {
        setBusy(true);
        say(null);
        setProgress(tracksProgress ? 0 : null);
        try
        {
            const outcome = await command();
            if (outcome.outcome === "STALE") say(STALE_MESSAGE);
            return outcome.outcome === "STALE" ? "STALE" : "SAVED";
        }
        catch (refusal: unknown)
        {
            say(refusalMessage(refusal, fallback));
            return "REFUSED";
        }
        finally
        {
            setProgress(null);
            await list.reload();
            reporter?.reportEvidenceChanged();
            setBusy(false);
        }
    };

    const send = async (upload: KeptUpload) =>
    {
        if (!evidence) {
            keep(current => withKeptUpload(current, upload));
            return;
        }
        const target = upload.artifact
            ? evidence.artifacts.find(artifact => artifact.id === upload.artifact?.id) ?? upload.artifact
            : undefined;
        const result = target
            ? await run(
                () => commands.replace(
                    requestId,
                    requirementId,
                    target.id,
                    upload.file,
                    target.etag,
                    setProgress,
                    upload.idempotencyKey,
                    upload.details,
                ),
                "The file could not be replaced.",
                true,
            )
            : await run(
                () => commands.upload(
                    requestId,
                    requirementId,
                    upload.file,
                    evidence.evidenceETag,
                    setProgress,
                    upload.idempotencyKey,
                    upload.details,
                ),
                "The file could not be uploaded.",
                true,
            );
        keep(current => result === "SAVED" ? withoutKeptUpload(current, upload) : withKeptUpload(current, upload));
    };

    const upload = (file: File, details: EvidenceDetails) => send({file, details, idempotencyKey: crypto.randomUUID()});

    const replace = (artifact: InformationRequestEvidenceArtifactDto, file: File, details: EvidenceDetails) =>
        send({file, details, idempotencyKey: crypto.randomUUID(), artifact});

    const withdraw = async (artifact: InformationRequestEvidenceArtifactDto, reason: string) =>
    {
        await run(
            () => commands.withdraw(requestId, requirementId, artifact.id, reason, artifact.etag),
            "The file could not be withdrawn.",
            false,
        );
    };

    const open = async (
        artifact: InformationRequestEvidenceArtifactDto,
        version: InformationRequestEvidenceVersionDto,
        use: InformationRequestEvidenceContentUse,
    ) =>
    {
        say(null);
        const tab = use === "preview" ? openEvidencePreviewTab() : null;
        if (use === "preview" && !tab) {
            say(PREVIEW_BLOCKED_MESSAGE);
            return;
        }
        try
        {
            const content = await commands.open(requestId, requirementId, artifact.id, version.id, use);
            if (!stillOffered(artifact, version))
            {
                tab?.close();
                return;
            }
            const url = URL.createObjectURL(content);
            if (tab) {
                tab.show(url);
            }
            else
            {
                const anchor = document.createElement("a");
                anchor.href = url;
                anchor.download = version.declaredFileName ?? "evidence";
                anchor.click();
            }
            window.setTimeout(() => URL.revokeObjectURL(url), OBJECT_URL_LIFETIME_MS);
        }
        catch (refusal: unknown)
        {
            tab?.close();
            say(refusalMessage(refusal, "The file could not be opened."));
        }
    };

    return {
        evidence,
        loadError: list.error,
        reload: list.reload,
        busy,
        progress,
        message: valueInScope(message, scope),
        kept,
        retry: upload => send(upload),
        discard: upload => keep(current => withoutKeptUpload(current, upload)),
        upload,
        replace,
        withdraw,
        open,
    };
};
