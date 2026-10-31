import {
    InformationRequestSubmissionPreviewDto,
    InformationRequestSubmissionStageOrdering
} from "../../models/models.tsx";

export const submissionUnavailableReason = (preview: InformationRequestSubmissionPreviewDto): string | null => {
    if (preview.canSubmit || !preview.ready) return null;
    const selected = preview.stages.find(stage => stage.stageKey === preview.stageKey);
    if (selected?.submitted && !selected.correctionOpen)
        return "This part is already submitted. It must be returned or withdrawn before you can change it.";
    if (preview.submissionStageOrdering === InformationRequestSubmissionStageOrdering.SEQUENTIAL && selected &&
        preview.stages.slice(0, preview.stages.indexOf(selected)).some(stage => !stage.submitted))
        return "Submit the earlier parts before submitting this part.";
    return "Submission is unavailable for your current access or this request's standing. Refresh the request or contact its administrator.";
};