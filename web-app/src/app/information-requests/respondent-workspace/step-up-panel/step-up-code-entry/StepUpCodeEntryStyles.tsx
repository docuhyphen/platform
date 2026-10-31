import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepUpCodeEntryStyles = makeStyles({
    codeEntry: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "flex-end",
        gap: tokens.spacingHorizontalM,
    },
    codeField: {
        flexGrow: 1,
        minWidth: "160px",
        maxWidth: "240px",
    },
});
