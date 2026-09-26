import {MessageBar, MessageBarBody, Spinner, Title2} from "@fluentui/react-components";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import {PlanFeature} from "../../../models/models.tsx";
import ReviewQueueList from "../review-queue-list/ReviewQueueList.tsx";
import {useInformationRequestReviewQueueStyles} from "./InformationRequestReviewQueueStyles.tsx";

const InformationRequestReviewQueue = () =>
{
    const styles = useInformationRequestReviewQueueStyles();
    const feature = usePlanFeature(PlanFeature.INFORMATION_REQUESTS);

    return (
        <section id={"information-request-review-queue-page"}
                 className={styles.page}>
            <Title2 id={"information-request-review-queue-title"}>Reviews assigned to you</Title2>
            {!feature.isKnown && (
                <Spinner id={"information-request-review-queue-plan-loading"}
                         size={"medium"}
                         label={"Checking your plan"}/>
            )}
            {feature.isKnown && !feature.isAvailable && (
                <MessageBar id={"information-request-review-queue-unavailable"}
                            intent={"warning"}>
                    <MessageBarBody>Information Requests are not included in your plan.</MessageBarBody>
                </MessageBar>
            )}
            {feature.isAvailable && <ReviewQueueList/>}
        </section>
    );
};

export default InformationRequestReviewQueue;
