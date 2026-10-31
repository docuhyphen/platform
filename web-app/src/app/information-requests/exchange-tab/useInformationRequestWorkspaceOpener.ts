import {useState} from "react";
import {useNavigate} from "react-router-dom";
import {
    getInformationRequestReviewQueue,
    listInformationRequestReviews
} from "../../../services/informationRequestReviewService.ts";
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

    const openLatestReview = async (requestId: string) => {
        setOpeningId(requestId);
        try {
            const latest = [...await listInformationRequestReviews(requestId)]
                .sort((first, second) => second.review.openedAt - first.review.openedAt)[0];
            navigate(latest ? informationRequestReviewPath(requestId, latest.review.id) : INFORMATION_REQUEST_REVIEW_QUEUE_PATH);
        } catch {
            navigate(INFORMATION_REQUEST_REVIEW_QUEUE_PATH);
        } finally {
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
                if (summary.permissions.canManage) navigate(informationRequestManagePath(summary.id));
                else if (summary.permissions.canReview && !summary.permissions.canRespond) await openLatestReview(summary.id);
                else navigate(informationRequestRespondPath(summary.id));
        }
    };

    const manage = (summary: InformationRequestSummaryDto) => navigate(informationRequestManagePath(summary.id));

    return {open, manage, openingId};
};
