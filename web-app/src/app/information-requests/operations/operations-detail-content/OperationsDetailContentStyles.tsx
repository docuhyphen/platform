import {makeStyles, tokens} from "@fluentui/react-components";

export const useOperationsDetailContentStyles = makeStyles({
    note: {
        color: tokens.colorNeutralForeground2,
    },
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        maxWidth: "1100px",
        minWidth: 0,
    },
    tabs: {
        maxWidth: "100%",
        overflowX: "auto",
        flexShrink: 0,
    },
});
