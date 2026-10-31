import {InformationRequestEvidenceListDto} from "../../models/models.tsx";
import {
    InformationRequestEvidenceAttributesInput,
    InformationRequestEvidenceCommandOutcome,
    InformationRequestEvidenceContentUse,
    listInformationRequestEvidence,
    openInformationRequestEvidenceContent,
    replaceInformationRequestEvidence,
    uploadInformationRequestEvidence,
    withdrawInformationRequestEvidence,
} from "../../../services/informationRequestEvidenceService.ts";
import {isInformationRequestAccessSessionEnded} from "../shared/informationRequestRefusal.ts";
import {stableKeyedCommand} from "../shared/useStableIdempotencyKey.ts";

export interface RequirementEvidenceCommandOptions
{
    accessLinkToken?: string;
    newIdempotencyKey?: () => string;
    commandKeys?: Map<string, string>;
    onAccessSessionEnded?: (refusal: unknown) => void;
}

export interface RequirementEvidenceCommands
{
    list: (requestId: string, requirementId: string) => Promise<InformationRequestEvidenceListDto>;
    upload: (
        requestId: string,
        requirementId: string,
        file: File,
        evidenceETag: string,
        onProgress: (percent: number) => void,
        idempotencyKey: string,
        attributes: InformationRequestEvidenceAttributesInput,
    ) => Promise<InformationRequestEvidenceCommandOutcome>;
    replace: (
        requestId: string,
        requirementId: string,
        artifactId: string,
        file: File,
        artifactETag: string,
        onProgress: (percent: number) => void,
        idempotencyKey: string,
        attributes: InformationRequestEvidenceAttributesInput,
    ) => Promise<InformationRequestEvidenceCommandOutcome>;
    withdraw: (
        requestId: string,
        requirementId: string,
        artifactId: string,
        reason: string,
        artifactETag: string,
    ) => Promise<InformationRequestEvidenceCommandOutcome>;
    open: (
        requestId: string,
        requirementId: string,
        artifactId: string,
        versionId: string,
        use: InformationRequestEvidenceContentUse,
    ) => Promise<Blob>;
}

export const requirementEvidenceCommands = (
    options: RequirementEvidenceCommandOptions = {},
): RequirementEvidenceCommands =>
{
    const keyed = stableKeyedCommand(options.commandKeys ?? new Map(), options.newIdempotencyKey);
    const accessLinkToken = options.accessLinkToken;
    const reportingEndedSession = <T>(operation: Promise<T>): Promise<T> =>
        operation.catch((refusal: unknown) => {
            if (isInformationRequestAccessSessionEnded(refusal)) options.onAccessSessionEnded?.(refusal);
            throw refusal;
        });

    return {
        list: (requestId, requirementId) =>
            reportingEndedSession(listInformationRequestEvidence(requestId, requirementId, accessLinkToken)),
        upload: (requestId, requirementId, file, evidenceETag, onProgress, idempotencyKey, attributes) =>
            reportingEndedSession(uploadInformationRequestEvidence(requestId, requirementId, file, {
                expectedETag: evidenceETag,
                idempotencyKey,
                accessLinkToken,
                attributes,
                onProgress,
            })),
        replace: (requestId, requirementId, artifactId, file, artifactETag, onProgress, idempotencyKey, attributes) =>
            reportingEndedSession(replaceInformationRequestEvidence(requestId, requirementId, artifactId, file, {
                expectedETag: artifactETag,
                idempotencyKey,
                accessLinkToken,
                attributes,
                onProgress,
            })),
        withdraw: (requestId, requirementId, artifactId, reason, artifactETag) =>
            reportingEndedSession(keyed(
                `withdraw-evidence|${requestId}|${requirementId}|${artifactId}`,
                {reason, artifactETag},
                idempotencyKey => withdrawInformationRequestEvidence(requestId, requirementId, artifactId, reason, {
                    expectedETag: artifactETag,
                    idempotencyKey,
                    accessLinkToken,
                }),
            )),
        open: (requestId, requirementId, artifactId, versionId, use) =>
            reportingEndedSession(
                openInformationRequestEvidenceContent(requestId, requirementId, artifactId, versionId, use, accessLinkToken),
            ),
    };
};
