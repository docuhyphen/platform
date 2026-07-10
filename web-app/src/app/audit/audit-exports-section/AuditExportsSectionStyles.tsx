import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditExportsSectionStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
    },
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
});
