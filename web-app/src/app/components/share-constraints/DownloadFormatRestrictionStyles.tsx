import {tokens, makeStyles} from "@fluentui/react-components";

export const useDownloadFormatRestrictionStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        marginLeft: tokens.spacingHorizontalXXL,
        marginTop: tokens.spacingVerticalXS,
    },
    formatCheckboxes: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
        marginLeft: tokens.spacingHorizontalXXL,
    },
});
