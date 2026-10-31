import {Title2, Title3} from "@fluentui/react-components";
import ReviewQueueList from "../../review/review-queue-list/ReviewQueueList.tsx";
import AssignedRequestList from "../assigned-request-list/AssignedRequestList.tsx";
import {useInformationRequestAssignedWorkStyles} from "./InformationRequestAssignedWorkStyles.tsx";

const InformationRequestAssignedWork = () => {
    const styles = useInformationRequestAssignedWorkStyles();

    return (
        <section id={"information-request-assigned-work-page"}
                 className={styles.page}>
            <Title2 id={"information-request-assigned-work-title"}
                    as={"h1"}>
                Assigned to you
            </Title2>
            <section id={"information-request-assigned-work-requests"}
                     aria-labelledby={"information-request-assigned-work-requests-title"}
                     className={styles.section}>
                <Title3 id={"information-request-assigned-work-requests-title"}
                        as={"h2"}>
                    Requests
                </Title3>
                <AssignedRequestList/>
            </section>
            <section id={"information-request-assigned-work-reviews"}
                     aria-labelledby={"information-request-assigned-work-reviews-title"}
                     className={styles.section}>
                <Title3 id={"information-request-assigned-work-reviews-title"}
                        as={"h2"}>
                    Reviews assigned to you
                </Title3>
                <ReviewQueueList/>
            </section>
        </section>
    );
};

export default InformationRequestAssignedWork;
