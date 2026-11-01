import {makeStyles, tokens} from "@fluentui/react-components";

export const useVariableTokenInputStyles = makeStyles({
    root: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },
    inputWrapper: {
        position: "relative",
    },
    fullWidth: {
        width: "100%",
    },
    insertButton: {
        fontSize: tokens.fontSizeBase200,
        padding: `0 ${tokens.spacingHorizontalXS}`,
        minWidth: 0,
    },
});
