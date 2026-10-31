import {useEffect, useLayoutEffect, useRef, useState} from "react";
import {
    InformationRequestEvidenceContentUse,
    openInformationRequestSubmittedEvidenceContent,
} from "../../../../services/informationRequestEvidenceService.ts";
import {InformationRequestReviewItemDto, InformationRequestSubmissionEvidenceDto} from "../../../models/models.tsx";
import {Scoped, useScopeGuard, valueInScope} from "../../shared/useScopeGuard.ts";
import {useStillMounted} from "../../shared/useStillMounted.ts";
import {PreviewedReviewEvidence} from "../model/PreviewedReviewEvidence.ts";
import {downloadReviewEvidence} from "../reviewEvidenceContent.ts";

const OPEN_FAILURE = "The file could not be opened.";

export interface ReviewEvidenceOpening {
    failure: string | null;
    preview: PreviewedReviewEvidence | null;
    closePreview: () => void;
    open: (evidence: InformationRequestSubmissionEvidenceDto, use: InformationRequestEvidenceContentUse) => Promise<void>;
}

const offeredVersionsOf = (item: InformationRequestReviewItemDto): ReadonlySet<string> =>
    new Set(item.contentVisible ? item.evidence.map(file => file.evidenceVersionId) : []);

export const useReviewEvidenceOpening = (
    requestId: string,
    packageId: string,
    item: InformationRequestReviewItemDto,
): ReviewEvidenceOpening => {
    const scope = `${requestId}|${packageId}|${item.submissionItemId}|${item.requirementId}`;
    const guard = useScopeGuard(scope);
    const stillMounted = useStillMounted();
    const offered = useRef(offeredVersionsOf(item));
    const [failure, setFailure] = useState<Scoped<string> | null>(null);
    const [preview, setPreview] = useState<Scoped<PreviewedReviewEvidence> | null>(null);
    const shownPreview = valueInScope(preview, scope);

    useLayoutEffect(() => {
        offered.current = offeredVersionsOf(item);
        setPreview(current => current && (current.scope !== scope || !offered.current.has(current.value.versionId)) ? null : current);
    }, [item, scope]);

    useEffect(() => () => {
        if (shownPreview) URL.revokeObjectURL(shownPreview.url);
    }, [shownPreview]);

    const stillOffered = (evidence: InformationRequestSubmissionEvidenceDto): boolean =>
        stillMounted() && guard.isCurrent(scope) && offered.current.has(evidence.evidenceVersionId);

    const open = async (evidence: InformationRequestSubmissionEvidenceDto, use: InformationRequestEvidenceContentUse) => {
        setFailure(null);
        try {
            const content = await openInformationRequestSubmittedEvidenceContent(
                requestId,
                packageId,
                evidence.evidenceVersionId,
                use,
            );
            if (!stillOffered(evidence)) return;
            if (use === "preview") setPreview({
                scope, value: {
                    versionId: evidence.evidenceVersionId,
                    url: URL.createObjectURL(content),
                    fileName: evidence.declaredFileName ?? `File version ${evidence.versionNumber}`,
                }
            });
            else downloadReviewEvidence(content, evidence);
        } catch {
            if (stillOffered(evidence)) setFailure({scope, value: OPEN_FAILURE});
        }
    };

    return {failure: valueInScope(failure, scope), preview: shownPreview, closePreview: () => setPreview(null), open};
};
