import {makeStyles, tokens} from "@fluentui/react-components";

export const useCompletionGateControlStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: tokens.spacingVerticalS,
        minWidth: 0,
    },
    state: {
        overflowWrap: "anywhere",
    },
    hint: {
        color: tokens.colorNeutralForeground2,
        overflowWrap: "anywhere",
    },
});
