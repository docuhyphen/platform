import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditHistoryPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
    },
    list: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        margin: 0,
        padding: 0,
        listStyleType: "none",
    },
    event: {
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
        overflowWrap: "anywhere",
    },
    detail: {
        color: tokens.colorNeutralForeground3,
        overflowWrap: "anywhere",
    },
});
