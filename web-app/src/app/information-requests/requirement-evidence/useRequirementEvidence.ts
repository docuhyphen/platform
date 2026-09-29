import {useCallback, useEffect, useState} from "react";
import {
    InformationRequestEvidenceArtifactDto,
    InformationRequestEvidenceListDto,
    InformationRequestEvidenceVersionDto,
} from "../../models/models.tsx";
import {
    InformationRequestEvidenceCommandOutcome,
    InformationRequestEvidenceContentUse,
} from "../../../services/informationRequestEvidenceService.ts";
import {RequirementEvidenceCommands} from "./requirementEvidenceCommands.ts";
import {refusalMessage} from "./requirementEvidenceLabels.ts";

const STALE_MESSAGE = "This evidence changed while you were working. The latest files are shown, so try again.";
const OBJECT_URL_LIFETIME_MS = 60_000;

export interface KeptUpload
{
    file: File;
    idempotencyKey: string;
    artifact?: InformationRequestEvidenceArtifactDto;
}

type EvidenceCommandResult = "SAVED" | "STALE" | "REFUSED";

export interface RequirementEvidence
{
    evidence: InformationRequestEvidenceListDto | null;
    busy: boolean;
    progress: number | null;
    message: string | null;
    kept: KeptUpload | null;
    retry: () => Promise<void>;
    discard: () => void;
    upload: (file: File) => Promise<void>;
    replace: (artifact: InformationRequestEvidenceArtifactDto, file: File) => Promise<void>;
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
): RequirementEvidence =>
{
    const [evidence, setEvidence] = useState<InformationRequestEvidenceListDto | null>(null);
    const [busy, setBusy] = useState(false);
    const [progress, setProgress] = useState<number | null>(null);
    const [message, setMessage] = useState<string | null>(null);
    const [kept, setKept] = useState<KeptUpload | null>(null);

    const load = useCallback(async () =>
    {
        try
        {
            setEvidence(await commands.list(requestId, requirementId));
        }
        catch (refusal: unknown)
        {
            setMessage(refusalMessage(refusal, "The evidence for this Requirement could not be loaded."));
        }
    }, [commands, requestId, requirementId]);

    useEffect(() =>
    {
        void load();
    }, [load]);

    const run = async (
        command: () => Promise<InformationRequestEvidenceCommandOutcome>,
        fallback: string,
        tracksProgress: boolean,
    ): Promise<EvidenceCommandResult> =>
    {
        setBusy(true);
        setMessage(null);
        setProgress(tracksProgress ? 0 : null);
        try
        {
            const outcome = await command();
            if (outcome.outcome === "STALE") setMessage(STALE_MESSAGE);
            return outcome.outcome === "STALE" ? "STALE" : "SAVED";
        }
        catch (refusal: unknown)
        {
            setMessage(refusalMessage(refusal, fallback));
            return "REFUSED";
        }
        finally
        {
            setProgress(null);
            await load();
            setBusy(false);
        }
    };

    const send = async (upload: KeptUpload) =>
    {
        const target = upload.artifact;
        const result = target
            ? await run(
                () => commands.replace(requestId, requirementId, target.id, upload.file, target.etag, setProgress, upload.idempotencyKey),
                "The file could not be replaced.",
                true,
            )
            : evidence
                ? await run(
                    () => commands.upload(requestId, requirementId, upload.file, evidence.evidenceETag, setProgress, upload.idempotencyKey),
                    "The file could not be uploaded.",
                    true,
                )
                : "STALE";
        setKept(result === "REFUSED" ? upload : null);
    };

    const upload = (file: File) => send({file, idempotencyKey: crypto.randomUUID()});

    const replace = (artifact: InformationRequestEvidenceArtifactDto, file: File) =>
        send({file, idempotencyKey: crypto.randomUUID(), artifact});

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
        setMessage(null);
        try
        {
            const url = URL.createObjectURL(await commands.open(requestId, requirementId, artifact.id, version.id, use));
            if (use === "preview")
            {
                window.open(url, "_blank", "noopener");
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
            setMessage(refusalMessage(refusal, "The file could not be opened."));
        }
    };

    return {
        evidence,
        busy,
        progress,
        message,
        kept,
        retry: async () =>
        {
            if (kept) await send(kept);
        },
        discard: () => setKept(null),
        upload,
        replace,
        withdraw,
        open,
    };
};
