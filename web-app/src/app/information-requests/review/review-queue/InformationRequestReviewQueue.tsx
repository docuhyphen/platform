import {Title2} from "@fluentui/react-components";
import ReviewQueueList from "../review-queue-list/ReviewQueueList.tsx";
import {useInformationRequestReviewQueueStyles} from "./InformationRequestReviewQueueStyles.tsx";

const InformationRequestReviewQueue = () =>
{
    const styles = useInformationRequestReviewQueueStyles();

    return (
        <section id={"information-request-review-queue-page"}
                 className={styles.page}>
            <Title2 id={"information-request-review-queue-title"}>Reviews assigned to you</Title2>
            <ReviewQueueList/>
        </section>
    );
};

export default InformationRequestReviewQueue;
