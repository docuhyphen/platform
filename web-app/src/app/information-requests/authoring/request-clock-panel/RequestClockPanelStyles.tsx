import {makeStyles, tokens} from "@fluentui/react-components";

export const useRequestClockPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    list: {
        listStyleType: "none",
        marginTop: 0,
        marginBottom: 0,
        paddingLeft: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    clock: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
        paddingBottom: tokens.spacingVerticalS,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },
    clockText: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
    },
    muted: {
        color: tokens.colorNeutralForeground2,
    },
});
