import React from "react";
import {Text} from "@fluentui/react-components";
import {DocumentDetailedDto, NoAuthExchangeBasicDto} from "../../../models/models.tsx";
import NoAuthExchangeDocumentList from "../document-list/NoAuthExchangeDocumentList.tsx";
import NoAuthExchangeOverview from "../exchange-overview/NoAuthExchangeOverview.tsx";
import {useNoAuthExchangeWorkspaceStyles} from "./NoAuthExchangeWorkspaceStyles.tsx";

interface NoAuthExchangeWorkspaceProps
{
    exchange: NoAuthExchangeBasicDto;
    onDocumentUploaded: (document: DocumentDetailedDto) => void;
}

const NoAuthExchangeWorkspace: React.FC<NoAuthExchangeWorkspaceProps> = ({exchange, onDocumentUploaded}) =>
{
    const styles = useNoAuthExchangeWorkspaceStyles();

    return (
        <main
            id={"no-auth-exchange-workspace"}
            className={styles.container}
        >
            <NoAuthExchangeOverview exchange={exchange}/>
            <div
                id={"no-auth-exchange-document-workspace"}
                className={styles.documentWorkspace}
            >
                <div className={styles.workspaceHeading}>
                    <div className={styles.workspaceTitleGroup}>
                        <Text
                            size={600}
                            weight={"semibold"}
                        >
                            Exchange documents
                        </Text>
                        <Text className={styles.workspaceDescription}>
                            Review available documents or upload a file where one is needed.
                        </Text>
                    </div>
                    <Text className={styles.privateLabel}>Private exchange</Text>
                </div>
                <NoAuthExchangeDocumentList exchange={exchange} onDocumentUploaded={onDocumentUploaded}/>
            </div>
        </main>
    );
};

export default NoAuthExchangeWorkspace;
