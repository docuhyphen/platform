import {InformationRequestEvidenceListDto} from "../../models/models.tsx";
import {
    InformationRequestEvidenceCommandOutcome,
    InformationRequestEvidenceContentUse,
    listInformationRequestEvidence,
    openInformationRequestEvidenceContent,
    replaceInformationRequestEvidence,
    uploadInformationRequestEvidence,
    withdrawInformationRequestEvidence,
} from "../../../services/informationRequestEvidenceService.ts";

export interface RequirementEvidenceCommandOptions
{
    accessLinkToken?: string;
    newIdempotencyKey?: () => string;
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
    ) => Promise<InformationRequestEvidenceCommandOutcome>;
    replace: (
        requestId: string,
        requirementId: string,
        artifactId: string,
        file: File,
        artifactETag: string,
        onProgress: (percent: number) => void,
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
    const nextIdempotencyKey = options.newIdempotencyKey ?? (() => crypto.randomUUID());
    const accessLinkToken = options.accessLinkToken;

    return {
        list: (requestId, requirementId) =>
            listInformationRequestEvidence(requestId, requirementId, accessLinkToken),
        upload: (requestId, requirementId, file, evidenceETag, onProgress) =>
            uploadInformationRequestEvidence(requestId, requirementId, file, {
                expectedETag: evidenceETag,
                idempotencyKey: nextIdempotencyKey(),
                accessLinkToken,
                onProgress,
            }),
        replace: (requestId, requirementId, artifactId, file, artifactETag, onProgress) =>
            replaceInformationRequestEvidence(requestId, requirementId, artifactId, file, {
                expectedETag: artifactETag,
                idempotencyKey: nextIdempotencyKey(),
                accessLinkToken,
                onProgress,
            }),
        withdraw: (requestId, requirementId, artifactId, reason, artifactETag) =>
            withdrawInformationRequestEvidence(requestId, requirementId, artifactId, reason, {
                expectedETag: artifactETag,
                idempotencyKey: nextIdempotencyKey(),
                accessLinkToken,
            }),
        open: (requestId, requirementId, artifactId, versionId, use) =>
            openInformationRequestEvidenceContent(requestId, requirementId, artifactId, versionId, use, accessLinkToken),
    };
};
