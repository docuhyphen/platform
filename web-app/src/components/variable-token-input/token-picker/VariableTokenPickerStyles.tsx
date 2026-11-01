import {makeStyles, tokens} from "@fluentui/react-components";

export const useVariableTokenPickerStyles = makeStyles({
    pickerSurface: {
        padding: tokens.spacingHorizontalM,
        minWidth: "260px",
        maxWidth: "320px",
        maxHeight: "400px",
        overflowY: "auto",
    },
    pickerTitle: {
        marginBottom: tokens.spacingVerticalS,
    },
    pickerSearch: {
        marginBottom: tokens.spacingVerticalS,
        width: "100%",
    },
});
