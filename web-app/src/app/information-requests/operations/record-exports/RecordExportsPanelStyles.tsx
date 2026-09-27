import {makeStyles, tokens} from "@fluentui/react-components";

export const useRecordExportsPanelStyles = makeStyles({
    card: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingVerticalM,
        borderRadius: tokens.borderRadiusMedium,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
        minWidth: 0,
    },
    heading: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
    },
    list: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        margin: 0,
        padding: 0,
        listStyleType: "none",
    },
    entry: {
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
        minWidth: "200px",
    },
    detail: {
        color: tokens.colorNeutralForeground3,
        overflowWrap: "anywhere",
    },
});
