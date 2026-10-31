import {Spinner, Text} from "@fluentui/react-components";
import InformationRequestSummaryRow
    from "../../exchange-tab/information-request-summary-row/InformationRequestSummaryRow.tsx";
import {useInformationRequestWorkspaceOpener} from "../../exchange-tab/useInformationRequestWorkspaceOpener.ts";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import {useAssignedInformationRequests} from "../useAssignedInformationRequests.ts";
import {useAssignedRequestListStyles} from "./AssignedRequestListStyles.tsx";

const AssignedRequestList = () => {
    const styles = useAssignedRequestListStyles();
    const {entries, error, reload} = useAssignedInformationRequests();
    const {open, openingId} = useInformationRequestWorkspaceOpener();

    if (error) {
        return (
            <InformationRequestLoadError id={"information-request-assigned-work-error"}
                                         message={error}
                                         onRetry={reload}/>
        );
    }
    if (!entries) {
        return (
            <Spinner id={"information-request-assigned-work-loading"}
                     size={"medium"}
                     label={"Loading your requests"}/>
        );
    }
    if (entries.length === 0) {
        return <Text id={"information-request-assigned-work-empty"}>No Information Requests are assigned to you.</Text>;
    }

    return (
        <ul id={"information-request-assigned-work-list"}
            aria-label={"Information Requests assigned to you"}
            className={styles.list}>
            {entries.map(entry => (
                <InformationRequestSummaryRow key={entry.request.id}
                                              summary={entry.request}
                                              context={entry.exchangeName ? `In ${entry.exchangeName}` : undefined}
                                              opening={openingId === entry.request.id}
                                              onOpen={open}/>
            ))}
        </ul>
    );
};

export default AssignedRequestList;
