import React from "react";
import {Text} from "@fluentui/react-components";
import {useLinkedAccountsTabStyles} from "../LinkedAccountsTabStyles.tsx";

interface LinkedAccountsSummaryProps
{
    connectedCount: number;
}

const LinkedAccountsSummary: React.FC<LinkedAccountsSummaryProps> = (
    {
        connectedCount
    }
) =>
{
    const styles = useLinkedAccountsTabStyles();

    return (
        <>
            <div
                id={"linked-accounts-summary"}
                className={styles.summaryPanel}>
                <div
                    id={"linked-accounts-summary-copy"}
                    className={styles.summaryCopy}>
                    <Text
                        id={"linked-accounts-summary-count"}
                        size={500}
                        weight={"semibold"}>
                        {connectedCount} connected {connectedCount === 1 ? "account" : "accounts"}
                    </Text>
                    <Text
                        id={"linked-accounts-summary-detail"}
                        size={200}
                        className={styles.summaryDetail}>
                        You can sign in using any connected provider.
                    </Text>
                </div>
                <Text
                    id={"linked-accounts-summary-note"}
                    size={200}
                    className={styles.summaryNote}>
                    Keep at least one sign-in method connected at all times.
                </Text>
            </div>
        </>
    );
};

export default LinkedAccountsSummary;
