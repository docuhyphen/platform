import {makeStyles, tokens} from "@fluentui/react-components";

export const useAccessManagementDialogStyles = makeStyles({
    titleRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
        width: "100%",
    },

    tabList: {
        marginTop: tokens.spacingVerticalS,
    },

    tabPanel: {
        marginTop: tokens.spacingVerticalM,
    },

    layout: {
        marginTop: tokens.spacingVerticalM,
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: tokens.spacingHorizontalXL,
        '@media (max-width: 960px)': {
            gridTemplateColumns: "1fr",
        },
    },

    peopleSection: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },

    personCard: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalM}`,
        gap: tokens.spacingHorizontalS,
    },

    personDetails: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
    },

    personEmail: {
        color: tokens.colorNeutralForeground3,
        overflowWrap: "anywhere",
    },

    metaGrid: {
        display: "grid",
        gridTemplateColumns: "160px 1fr",
        rowGap: tokens.spacingVerticalS,
        columnGap: tokens.spacingHorizontalS,
    },

    switchGroup: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },

    requireSignInField: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        '@media (max-width: 640px)': {
            flexDirection: "column",
            alignItems: "stretch",
        },
    }
});
