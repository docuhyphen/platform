import {makeStyles, tokens} from "@fluentui/react-components";

export const useConditionExpressionBuilderStyles = makeStyles({
    field: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
        gridColumn: "1 / -1",
    },
    row: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        alignItems: "center",
    },
    fieldCombobox: {
        flex: "1 1 10rem",
        minWidth: 0,
    },
    operatorSelect: {
        flex: "1 1 10rem",
        minWidth: 0,
    },
    valueControl: {
        flex: "1 1 8rem",
        minWidth: 0,
    },
    hint: {
        color: tokens.colorNeutralForeground3,
    },
    sublabel: {
        color: tokens.colorNeutralForeground3,
        marginLeft: tokens.spacingHorizontalS,
        fontSize: "0.8em",
    },
});
