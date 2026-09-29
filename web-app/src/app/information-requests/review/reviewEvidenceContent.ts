import {
    InformationRequestEvidenceContentUse,
    openInformationRequestEvidenceContent,
} from "../../../services/informationRequestEvidenceService.ts";
import {InformationRequestSubmissionEvidenceDto} from "../../models/models.tsx";

const OBJECT_URL_LIFETIME_MS = 60_000;

export const openReviewEvidence = async (
    requestId: string,
    requirementId: string,
    evidence: InformationRequestSubmissionEvidenceDto,
    use: InformationRequestEvidenceContentUse,
) =>
{
    const content = await openInformationRequestEvidenceContent(requestId, requirementId, evidence.artifactId, evidence.evidenceVersionId, use);
    const url = URL.createObjectURL(content);
    if (use === "preview")
    {
        window.open(url, "_blank", "noopener");
    }
    else
    {
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = `evidence-version-${evidence.versionNumber}`;
        anchor.click();
    }
    window.setTimeout(() => URL.revokeObjectURL(url), OBJECT_URL_LIFETIME_MS);
};
