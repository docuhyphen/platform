import {makeStyles, tokens} from "@fluentui/react-components";

export const usePartyPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    header: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    headerActions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
    },
    list: {
        listStyleType: "none",
        marginTop: 0,
        marginBottom: 0,
        paddingLeft: 0,
        borderTop: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },
    muted: {
        color: tokens.colorNeutralForeground2,
    },
});
