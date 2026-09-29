import {makeStyles, tokens} from "@fluentui/react-components";

export const useRecordHoldRowStyles = makeStyles({
    row: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
        paddingTop: tokens.spacingVerticalS,
        borderTop: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke3}`,
    },
    summary: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        flex: 1,
        minWidth: "220px",
        overflowWrap: "anywhere",
    },
    detail: {
        color: tokens.colorNeutralForeground3,
    },
    actions: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
});
