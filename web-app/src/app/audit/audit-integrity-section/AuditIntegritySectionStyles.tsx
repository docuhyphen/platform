import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditIntegritySectionStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
    },
    summary: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },
    streamCard: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        padding: tokens.spacingVerticalM,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
    },
    streamHeader: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    failureNote: {
        color: tokens.colorPaletteRedForeground1,
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
});
