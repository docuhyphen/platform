import {useState} from "react";
import {useNavigate} from "react-router-dom";
import {getInformationRequestReviewQueue} from "../../../services/informationRequestReviewService.ts";
import {InformationRequestNextAction, InformationRequestSummaryDto} from "../../models/models.tsx";
import {
    INFORMATION_REQUEST_REVIEW_QUEUE_PATH,
    informationRequestManagePath,
    informationRequestRespondPath,
    informationRequestReviewPath,
} from "../shared/informationRequestWorkspacePaths.ts";

export const useInformationRequestWorkspaceOpener = () =>
{
    const navigate = useNavigate();
    const [openingId, setOpeningId] = useState<string | null>(null);

    const openReview = async (requestId: string) =>
    {
        setOpeningId(requestId);
        try
        {
            const entry = (await getInformationRequestReviewQueue()).find(candidate => candidate.informationRequestId === requestId);
            navigate(entry ? informationRequestReviewPath(requestId, entry.reviewId) : INFORMATION_REQUEST_REVIEW_QUEUE_PATH);
        }
        catch
        {
            navigate(INFORMATION_REQUEST_REVIEW_QUEUE_PATH);
        }
        finally
        {
            setOpeningId(null);
        }
    };

    const open = async (summary: InformationRequestSummaryDto) =>
    {
        switch (summary.nextAction)
        {
            case InformationRequestNextAction.REVIEW:
                await openReview(summary.id);
                return;
            case InformationRequestNextAction.COMPLETE_SETUP:
            case InformationRequestNextAction.MANAGE:
                navigate(informationRequestManagePath(summary.id));
                return;
            case InformationRequestNextAction.RESPOND:
                navigate(informationRequestRespondPath(summary.id));
                return;
            case InformationRequestNextAction.VIEW:
                navigate(summary.permissions.canManage
                    ? informationRequestManagePath(summary.id)
                    : informationRequestRespondPath(summary.id));
        }
    };

    return {open, openingId};
};
