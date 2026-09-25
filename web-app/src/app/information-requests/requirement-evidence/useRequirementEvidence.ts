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

export interface RequirementEvidence
{
    evidence: InformationRequestEvidenceListDto | null;
    busy: boolean;
    progress: number | null;
    message: string | null;
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
    ) =>
    {
        setBusy(true);
        setMessage(null);
        setProgress(tracksProgress ? 0 : null);
        try
        {
            const outcome = await command();
            if (outcome.outcome === "STALE") setMessage(STALE_MESSAGE);
        }
        catch (refusal: unknown)
        {
            setMessage(refusalMessage(refusal, fallback));
        }
        finally
        {
            setProgress(null);
            await load();
            setBusy(false);
        }
    };

    const upload = async (file: File) =>
    {
        if (!evidence) return;
        await run(
            () => commands.upload(requestId, requirementId, file, evidence.evidenceETag, setProgress),
            "The file could not be uploaded.",
            true,
        );
    };

    const replace = (artifact: InformationRequestEvidenceArtifactDto, file: File) =>
        run(
            () => commands.replace(requestId, requirementId, artifact.id, file, artifact.etag, setProgress),
            "The file could not be replaced.",
            true,
        );

    const withdraw = (artifact: InformationRequestEvidenceArtifactDto, reason: string) =>
        run(
            () => commands.withdraw(requestId, requirementId, artifact.id, reason, artifact.etag),
            "The file could not be withdrawn.",
            false,
        );

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

    return {evidence, busy, progress, message, upload, replace, withdraw, open};
};
