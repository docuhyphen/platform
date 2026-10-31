import {InformationRequestSubmissionEvidenceDto} from "../../models/models.tsx";

const OBJECT_URL_LIFETIME_MS = 60_000;

export const downloadReviewEvidence = (content: Blob, evidence: InformationRequestSubmissionEvidenceDto) =>
{
    const url = URL.createObjectURL(content);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = evidence.declaredFileName ?? `evidence-version-${evidence.versionNumber}`;
    anchor.click();
    window.setTimeout(() => URL.revokeObjectURL(url), OBJECT_URL_LIFETIME_MS);
};