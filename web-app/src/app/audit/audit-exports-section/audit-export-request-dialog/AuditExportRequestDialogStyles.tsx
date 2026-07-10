import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditExportRequestDialogStyles = makeStyles({
    body: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },
    formField: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
    },
    row: {
        display: "flex",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
});
