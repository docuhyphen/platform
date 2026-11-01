import {makeStyles, tokens} from "@fluentui/react-components";

export const useVariableTokenChipStyles = makeStyles({
    tokenChip: {
        cursor: "default",
        display: "inline-flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXXS,
        margin: `0 ${tokens.spacingHorizontalXXS}`,
    },
    removeIcon: {
        fontSize: tokens.fontSizeBase100,
    },
    removeButton: {
        minWidth: 0,
        padding: `0 ${tokens.spacingHorizontalXXS}`,
        height: "16px",
    },
});
