import {makeStyles, tokens} from "@fluentui/react-components";

export const useOutcomeFieldStyles = makeStyles({
    outcomeFieldColumn: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
    },

    outcomeFieldRow: {
        display: "flex",
        gap: tokens.spacingHorizontalXS,
        flexWrap: "wrap",
        minWidth: 0,
    },

    outcomeEmitSelect: {
        flex: "1 1 10rem",
        minWidth: 0,
    },
});
