import {MessageBar, MessageBarBody, Spinner} from "@fluentui/react-components";
import {useParams} from "react-router-dom";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import {PlanFeature} from "../../../models/models.tsx";
import ReviewWorkspaceContent from "../review-workspace-content/ReviewWorkspaceContent.tsx";
import {useInformationRequestReviewWorkspaceStyles} from "./InformationRequestReviewWorkspaceStyles.tsx";

const InformationRequestReviewWorkspace = () =>
{
    const styles = useInformationRequestReviewWorkspaceStyles();
    const {requestId, reviewId} = useParams();
    const feature = usePlanFeature(PlanFeature.INFORMATION_REQUESTS);

    return (
        <section id={"information-request-review-page"}
                 className={styles.page}>
            {!feature.isKnown && (
                <Spinner id={"information-request-review-plan-loading"}
                         size={"medium"}
                         label={"Checking your plan"}/>
            )}
            {feature.isKnown && !feature.isAvailable && (
                <MessageBar id={"information-request-review-unavailable"}
                            intent={"warning"}>
                    <MessageBarBody>Information Requests are not included in your plan.</MessageBarBody>
                </MessageBar>
            )}
            {feature.isAvailable && requestId && reviewId && (
                <ReviewWorkspaceContent requestId={requestId}
                                        reviewId={reviewId}/>
            )}
        </section>
    );
};

export default InformationRequestReviewWorkspace;
