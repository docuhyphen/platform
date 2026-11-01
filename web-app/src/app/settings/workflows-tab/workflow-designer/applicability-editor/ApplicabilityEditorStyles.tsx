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
    conditionList: {
        display: "flex",
        flexDirection: "column",
        rowGap: tokens.spacingVerticalS,
    },
    hint: {
        color: tokens.colorNeutralForeground3,
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
        fontStyle: "italic",
    },
});
