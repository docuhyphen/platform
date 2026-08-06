import {tokens, makeStyles} from "@fluentui/react-components";

export const useExchangeInitiationRecipientsTabStyles = makeStyles({

    recipientsTabContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
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
    activeRecentContact: {
        "& .fui-Persona__primaryText, & .fui-Persona__secondaryText": {
            color: tokens.colorNeutralForegroundStaticInverted,
        },
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
