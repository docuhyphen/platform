import {makeStyles, tokens} from "@fluentui/react-components";

export const useApplicabilityEditorStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        rowGap: tokens.spacingVerticalM,
        width: "100%",
    },
    header: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        columnGap: tokens.spacingHorizontalM,
        rowGap: tokens.spacingVerticalS,
    },
    schemaPicker: {
        display: "flex",
        flexDirection: "column",
        rowGap: tokens.spacingVerticalXS,
        maxWidth: "360px",
        width: "100%",
    },
    conditionList: {
        display: "flex",
        flexDirection: "column",
        rowGap: tokens.spacingVerticalS,
    },
    row: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "flex-end",
        columnGap: tokens.spacingHorizontalS,
        rowGap: tokens.spacingVerticalXS,
        padding: tokens.spacingVerticalS,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
    },
    rowField: {
        display: "flex",
        flexDirection: "column",
        rowGap: tokens.spacingVerticalXXS,
        minWidth: "140px",
        flexGrow: 1,
    },
    hint: {
        color: tokens.colorNeutralForeground3,
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
        fontStyle: "italic",
    },
});
