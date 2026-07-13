import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditExportRequestFieldsStyles = makeStyles({
    body: {display: "flex", flexDirection: "column", gap: tokens.spacingVerticalM},
    field: {display: "flex", flexDirection: "column", gap: tokens.spacingVerticalXXS},
    row: {display: "flex", gap: tokens.spacingHorizontalM, flexWrap: "wrap"},
    error: {color: tokens.colorPaletteRedForeground1},
});
