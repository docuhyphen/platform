import React from "react";
import {Button, Text} from "@fluentui/react-components";
import {StartedInformationRequest} from "../../startedInformationRequest.ts";
import {useCreatedInformationRequestSummaryStyles} from "./CreatedInformationRequestSummaryStyles.tsx";

interface CreatedInformationRequestSummaryProps {
    request: StartedInformationRequest | null;
    onManage: (requestId: string) => void;
}

const CreatedInformationRequestSummary: React.FC<CreatedInformationRequestSummaryProps> = ({request, onManage}) => {
    const styles = useCreatedInformationRequestSummaryStyles();

    if (!request) {
        return (
            <Text id={"exchange-created-information-request-unnamed"}
                  size={200}
                  className={styles.unnamed}>
                {"A draft Information Request was created. Open it from the Exchange's Information Requests tab."}
            </Text>
        );
    }

    return (
        <div id={"exchange-created-information-request"}
             className={styles.summary}>
            <Text id={"exchange-created-information-request-label"}
                  size={200}
                  className={styles.label}>
                Information Request
            </Text>
            <Text id={"exchange-created-information-request-title"}
                  size={300}
                  weight={"semibold"}
                  className={styles.title}>
                {request.title}
            </Text>
            <Text id={"exchange-created-information-request-state"}
                  size={200}>
                Saved as a draft for you to review and issue.
            </Text>
            <Button id={"exchange-manage-information-request-btn"}
                    shape={"circular"}
                    onClick={() => onManage(request.id)}>
                Manage Information Request
            </Button>
        </div>
    );
};

export default CreatedInformationRequestSummary;
