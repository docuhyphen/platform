import {makeStyles, tokens} from "@fluentui/react-components";

export const useAccessManagementDialogStyles = makeStyles({
    titleRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: "12px",
        width: "100%",
    },

    tabList: {
        marginTop: "8px",
    },

    tabPanel: {
        marginTop: "12px",
    },

    layout: {
        marginTop: "12px",
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: "20px",
        '@media (max-width: 960px)': {
            gridTemplateColumns: "1fr",
        },
    },

    peopleSection: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
    },

    personCard: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: "10px 12px",
        gap: "8px",
    },

    personDetails: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        minWidth: 0,
    },

    personEmail: {
        color: tokens.colorNeutralForeground3,
        overflowWrap: "anywhere",
    },

    metaGrid: {
        display: "grid",
        gridTemplateColumns: "160px 1fr",
        rowGap: "8px",
        columnGap: "8px",
    },

    switchGroup: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
    },

    requireSignInField: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "8px",
        '@media (max-width: 640px)': {
            flexDirection: "column",
            alignItems: "stretch",
        },
    }
});
