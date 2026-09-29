import {makeStyles, tokens} from "@fluentui/react-components";

export const usePrivacyPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalL,
        maxWidth: "1100px",
        minWidth: 0,
    },
    section: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    heading: {
        marginTop: 0,
        marginBottom: 0,
    },
    list: {
        listStyleType: "none",
        margin: 0,
        padding: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    row: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalM,
        paddingTop: tokens.spacingVerticalS,
        paddingBottom: tokens.spacingVerticalS,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },
    text: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        flex: 1,
        minWidth: "220px",
        overflowWrap: "anywhere",
    },
    muted: {
        color: tokens.colorNeutralForeground2,
    },
});
