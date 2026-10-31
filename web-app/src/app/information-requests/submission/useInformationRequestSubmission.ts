import {useCallback, useLayoutEffect, useRef, useState} from "react";
import {patchInformationRequestResponses} from "../../../services/informationRequestRuntimeService.ts";
import {
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
import {isInformationRequestAccessSessionEnded} from "../shared/informationRequestRefusal.ts";
import {Scoped, valueInScope} from "../shared/useScopeGuard.ts";
import {useStableIdempotencyKey} from "../shared/useStableIdempotencyKey.ts";
import {submissionErrorMessage} from "./submissionLabels.ts";
import {AttestationExceptionInput} from "./attestationException.ts";
import {useSubmissionPreview} from "./useSubmissionPreview.ts";

export interface AttestationInput
{
    requirementId: string;
    decision: InformationRequestAttestationDecision;
    refusalReason?: string;
    externalSignatureReference?: string;
    partyId?: string;
}

export interface SubmissionState
{
    preview: InformationRequestSubmissionPreviewDto | null;
    reviewed: InformationRequestSubmissionPreviewDto | null;
    selectedStageKey: string | null;
    loading: boolean;
    result: InformationRequestSubmissionResultDto | null;
    busy: boolean;
    error: string | null;
    retry: () => Promise<void>;
    selectStage: (stageKey: string) => Promise<void>;
    submit: () => Promise<void>;
    attest: (input: AttestationInput) => Promise<void>;
    withdraw: (packageId: string, reasonCode?: string) => Promise<boolean>;
    recordException: (input: AttestationExceptionInput) => Promise<boolean>;
}

const STALE_MESSAGE = "The information changed after you reviewed it. Review it again before you continue.";

export const useInformationRequestSubmission = (
    requestId: string,
    responseETag: string,
    accessLinkToken: string | undefined,
    onChanged: () => void,
    newIdempotencyKey?: () => string,
    evidenceRevision = 0,
): SubmissionState =>
{
    const keyed = useStableIdempotencyKey(newIdempotencyKey);
    const scope = `${requestId}|${accessLinkToken ?? ""}`;
    const changed = useRef(onChanged);
    const notifyChanged = useCallback(() => changed.current(), []);
    const summary = useSubmissionPreview(requestId, `${responseETag}|${evidenceRevision}`, accessLinkToken, notifyChanged);
    const [result, setResult] = useState<Scoped<InformationRequestSubmissionResultDto> | null>(null);
    const [busy, setBusy] = useState(false);
    const [commandError, setCommandError] = useState<Scoped<string> | null>(null);
    const reviewed = summary.reviewed;

    useLayoutEffect(() =>
    {
        changed.current = onChanged;
    }, [onChanged]);

    const run = async (command: () => Promise<"SAVED" | "STALE">) =>
    {
        setBusy(true);
        setCommandError(null);
        try
        {
            const outcome = await command();
            if (outcome === "STALE") setCommandError({scope, value: STALE_MESSAGE});
            changed.current();
            return outcome === "SAVED";
        }
        catch (caught: unknown)
        {
            const refusal = caught as InformationRequestSubmissionRefusalDto;
            setCommandError({scope, value: submissionErrorMessage(refusal, "This could not be completed.")});
            if (isInformationRequestAccessSessionEnded(caught)) changed.current();
            return false;
        }
        finally
        {
            await summary.reload();
            setBusy(false);
        }
    };

    const options = (expectedETag: string, idempotencyKey: string) => ({expectedETag, idempotencyKey, accessLinkToken});

    return {
        preview: summary.preview,
        reviewed,
        selectedStageKey: summary.selectedStageKey,
        loading: summary.loading,
        result: valueInScope(result, scope),
        busy,
        error: valueInScope(commandError, scope) ?? summary.error,
        retry: summary.reload,
        selectStage: summary.selectStage,
        submit: async () =>
        {
            if (!reviewed) return;
            const body = reviewed.stageKey ? {stageKey: reviewed.stageKey} : {};
            await run(async () => {
                const outcome = await keyed(`submit|${requestId}`, {
                    body,
                    expectedETag: reviewed.submissionETag
                }, idempotencyKey =>
                    submitInformationRequestPackage(requestId, body, options(reviewed.submissionETag, idempotencyKey)));
                if (outcome.outcome === "SAVED") setResult({scope, value: outcome.data});
                return outcome.outcome;
            });
        },
        attest: async (input: AttestationInput) =>
        {
            if (!reviewed) return;
            const {requirementId, ...request} = input;
            await run(async () =>
                (await keyed(`attest|${requestId}|${requirementId}`, {
                    request,
                    expectedETag: reviewed.submissionETag
                }, idempotencyKey =>
                    recordInformationRequestAttestation(requestId, requirementId, request, options(reviewed.submissionETag, idempotencyKey)))).outcome);
        },
        withdraw: (packageId: string, reasonCode?: string) => run(async () =>
            (await keyed(`withdraw-package|${requestId}|${packageId}`, {
                reasonCode,
                expectedETag: responseETag
            }, idempotencyKey =>
                withdrawInformationRequestPackage(requestId, packageId, {reasonCode}, options(responseETag, idempotencyKey)))).outcome),
        recordException: ({requirementId, disposition, reason}: AttestationExceptionInput) => run(async () =>
            (await keyed(`exception|${requestId}|${requirementId}`, {
                disposition,
                reason,
                expectedETag: responseETag
            }, idempotencyKey =>
                patchInformationRequestResponses(
                    requestId,
                    {patches: [{requirementId, disposition, narrative: reason}]},
                    options(responseETag, idempotencyKey),
                ))).outcome),
    };
};
