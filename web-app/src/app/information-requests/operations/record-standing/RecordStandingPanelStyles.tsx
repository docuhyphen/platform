import {makeStyles, tokens} from "@fluentui/react-components";

export const useRecordStandingPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
    },
    card: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        padding: tokens.spacingVerticalM,
        borderRadius: tokens.borderRadiusMedium,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
        minWidth: 0,
    },
    heading: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    detail: {
        color: tokens.colorNeutralForeground3,
        overflowWrap: "anywhere",
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
        paddingTop: tokens.spacingVerticalS,
    },
});
