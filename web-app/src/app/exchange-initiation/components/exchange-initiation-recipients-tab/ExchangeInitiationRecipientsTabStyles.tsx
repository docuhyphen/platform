import {makeStyles, shorthands} from "@fluentui/react-components";

export const useExchangeInitiationRecipientsTabStyles = makeStyles({

    recipientsTabContent: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
    },

    recipientEmailFields: {
        display: "flex",
        gap: "8px",
    },
    recipientEmail: {
        flex: 1
    },
    externalBadgeRow: {
        marginTop: "8px",
        display: "flex",
        alignItems: "center",
        gap: "8px",
    },
    roleSection: {
        marginTop: "16px",
        paddingTop: "12px",
        borderTop: "1px solid var(--colorNeutralStroke2)",
    },
    constraintsRow: {
        marginTop: "8px",
    },
    myGroupsOptionContent: {
        display: "flex",
        flexDirection: "column",
        gap: "2px",
    },
    recentContactsRow: {
        display: "flex",
        flexWrap: "wrap",
        gap: "8px",
    },
    myOrgSelectedList: {
        listStyleType: "none",
        marginBottom: "4px",
        marginTop: "0",
        paddingLeft: "0",
        display: "flex",
        gap: "4px",
        flexWrap: "wrap",
    },
    shareCountBadge: {
        marginLeft: "8px",
    },
});