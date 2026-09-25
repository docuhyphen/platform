import {useCallback, useEffect, useState} from "react";
import {
    getInformationRequestSubmissionPreview,
    recordInformationRequestAttestation,
    submitInformationRequestPackage,
    withdrawInformationRequestPackage,
} from "../../../services/informationRequestSubmissionService.ts";
import {
    InformationRequestAttestationDecision,
    InformationRequestSubmissionPreviewDto,
    InformationRequestSubmissionRefusalDto,
    InformationRequestSubmissionResultDto,
} from "../../models/models.tsx";
import {submissionErrorMessage} from "./submissionLabels.ts";

export interface AttestationInput
{
    requirementId: string;
    decision: InformationRequestAttestationDecision;
    refusalReason?: string;
    externalSignatureReference?: string;
}

export interface SubmissionState
{
    preview: InformationRequestSubmissionPreviewDto | null;
    result: InformationRequestSubmissionResultDto | null;
    busy: boolean;
    error: string | null;
    selectStage: (stageKey: string) => Promise<void>;
    submit: () => Promise<void>;
    attest: (input: AttestationInput) => Promise<void>;
    withdraw: (packageId: string) => Promise<void>;
}

const STALE_MESSAGE = "The information changed after you reviewed it. Review it again before you continue.";

export const useInformationRequestSubmission = (
    requestId: string,
    responseETag: string,
    accessLinkToken: string | undefined,
    onChanged: () => void,
    newIdempotencyKey: () => string = () => crypto.randomUUID(),
): SubmissionState =>
{
    const [preview, setPreview] = useState<InformationRequestSubmissionPreviewDto | null>(null);
    const [result, setResult] = useState<InformationRequestSubmissionResultDto | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async (stageKey?: string) =>
    {
        try
        {
            setPreview(await getInformationRequestSubmissionPreview(requestId, stageKey, accessLinkToken));
        }
        catch (caught: unknown)
        {
            setError(submissionErrorMessage(caught, "The submission summary could not be loaded."));
        }
    }, [accessLinkToken, requestId]);

    useEffect(() =>
    {
        void load();
    }, [load, responseETag]);

    const run = async (command: () => Promise<"SAVED" | "STALE">) =>
    {
        setBusy(true);
        setError(null);
        try
        {
            if (await command() === "STALE") setError(STALE_MESSAGE);
            onChanged();
        }
        catch (caught: unknown)
        {
            const refusal = caught as InformationRequestSubmissionRefusalDto;
            setError(submissionErrorMessage(refusal, "This could not be completed."));
        }
        finally
        {
            await load(preview?.stageKey);
            setBusy(false);
        }
    };

    const options = (expectedETag: string) => ({expectedETag, idempotencyKey: newIdempotencyKey(), accessLinkToken});

    return {
        preview,
        result,
        busy,
        error,
        selectStage: (stageKey: string) => load(stageKey),
        submit: () => run(async () =>
        {
            if (!preview) return "SAVED";
            const outcome = await submitInformationRequestPackage(
                requestId,
                preview.stageKey ? {stageKey: preview.stageKey} : {},
                options(preview.submissionETag),
            );
            if (outcome.outcome === "SAVED") setResult(outcome.data);
            return outcome.outcome;
        }),
        attest: (input: AttestationInput) => run(async () =>
        {
            if (!preview) return "SAVED";
            const {requirementId, ...request} = input;
            return (await recordInformationRequestAttestation(requestId, requirementId, request, options(preview.submissionETag))).outcome;
        }),
        withdraw: (packageId: string) => run(async () =>
            (await withdrawInformationRequestPackage(requestId, packageId, {}, options(responseETag))).outcome),
    };
};
