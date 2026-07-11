import {tokens, makeStyles, shorthands} from "@fluentui/react-components";

export const useExchangeInitiationRecipientsTabStyles = makeStyles({

    recipientsTabContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
    },

    recipientEmailFields: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
    },
    recipientEmail: {
        flex: 1
    },
    externalBadgeRow: {
        marginTop: tokens.spacingVerticalS,
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },
    roleSection: {
        marginTop: tokens.spacingVerticalL,
        paddingTop: tokens.spacingVerticalM,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
    },
    constraintsRow: {
        marginTop: tokens.spacingVerticalS,
    },
    myGroupsOptionContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXXS,
    },
    recentContactsRow: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    myOrgSelectedList: {
        listStyleType: "none",
        marginBottom: tokens.spacingVerticalXS,
        marginTop: "0",
        paddingLeft: "0",
        display: "flex",
        gap: tokens.spacingHorizontalXS,
        flexWrap: "wrap",
    },
    shareCountBadge: {
        marginLeft: tokens.spacingHorizontalS,
    },
});