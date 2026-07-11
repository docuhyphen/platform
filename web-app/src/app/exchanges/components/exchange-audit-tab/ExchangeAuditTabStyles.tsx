import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeAuditTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        padding: `${tokens.spacingVerticalS} 0`,
    },
    spinner: {
        padding: tokens.spacingHorizontalXXXL,
        alignSelf: "center",
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
        padding: tokens.spacingHorizontalL,
    },
    emptyText: {
        color: tokens.colorNeutralForeground4,
        fontStyle: "italic",
        padding: tokens.spacingHorizontalL,
    },
    table: {
        width: "100%",
    },
    docLabel: {
        color: tokens.colorNeutralForeground3,
        marginBottom: tokens.spacingVerticalXXS,
    },
    docGroup: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        marginBottom: tokens.spacingVerticalL,
    },
    docGroupHeader: {
        padding: `${tokens.spacingVerticalSNudge} 0 ${tokens.spacingVerticalXS} 0`,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        marginBottom: tokens.spacingVerticalXXS,
    },
});
