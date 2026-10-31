import {InformationRequestEvidenceArtifactDto} from "../../models/models.tsx";
import {EvidenceDetails} from "./evidenceDetails.ts";

export interface KeptUpload {
    file: File;
    details: EvidenceDetails;
    idempotencyKey: string;
    artifact?: InformationRequestEvidenceArtifactDto;
}

export interface KeptEvidenceUploads {
    kept: (requirementId: string) => KeptUpload[];
    keep: (requirementId: string, uploads: KeptUpload[]) => void;
}

export const keptEvidenceUploads = (store: Map<string, KeptUpload[]>, scope: string): KeptEvidenceUploads => {
    const keyOf = (requirementId: string) => `${scope}|${requirementId}`;
    return {
        kept: requirementId => store.get(keyOf(requirementId)) ?? [],
        keep: (requirementId, uploads) => {
            if (uploads.length > 0) store.set(keyOf(requirementId), uploads);
            else store.delete(keyOf(requirementId));
        },
    };
};

export const withKeptUpload = (uploads: KeptUpload[], upload: KeptUpload): KeptUpload[] =>
    uploads.some(kept => kept.idempotencyKey === upload.idempotencyKey)
        ? uploads.map(kept => kept.idempotencyKey === upload.idempotencyKey ? upload : kept)
        : [...uploads, upload];

export const withoutKeptUpload = (uploads: KeptUpload[], upload: KeptUpload): KeptUpload[] =>
    uploads.filter(kept => kept.idempotencyKey !== upload.idempotencyKey);
