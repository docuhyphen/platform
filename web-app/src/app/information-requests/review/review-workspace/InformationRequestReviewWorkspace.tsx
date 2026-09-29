import {useParams} from "react-router-dom";
import ReviewWorkspaceContent from "../review-workspace-content/ReviewWorkspaceContent.tsx";
import {useInformationRequestReviewWorkspaceStyles} from "./InformationRequestReviewWorkspaceStyles.tsx";

const InformationRequestReviewWorkspace = () =>
{
    const styles = useInformationRequestReviewWorkspaceStyles();
    const {requestId, reviewId} = useParams();

    return (
        <section id={"information-request-review-page"}
                 className={styles.page}>
            {requestId && reviewId && (
                <ReviewWorkspaceContent requestId={requestId}
                                        reviewId={reviewId}/>
            )}
        </section>
    );
};

export default InformationRequestReviewWorkspace;
