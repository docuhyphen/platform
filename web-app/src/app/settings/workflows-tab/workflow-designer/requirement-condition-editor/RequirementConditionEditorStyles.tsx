import {makeStyles, tokens} from "@fluentui/react-components";

export const useRequirementConditionEditorStyles = makeStyles({
    editor: {
        display: "flex",
        flexDirection: "column",
        rowGap: tokens.spacingVerticalM,
        width: "100%",
        minWidth: 0,
    },
    hint: {
        color: tokens.colorNeutralForeground3,
    },
    picker: {
        maxWidth: "360px",
        width: "100%",
    },
    list: {
        display: "flex",
        flexDirection: "column",
        rowGap: tokens.spacingVerticalS,
    },
    empty: {
        color: tokens.colorNeutralForeground3,
        fontStyle: "italic",
    },
});
