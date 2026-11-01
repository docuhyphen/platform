import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditEventDetailCardStyles = makeStyles({
    card: {
        display: "flex",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalM,
        padding: tokens.spacingVerticalS,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground3,
        minWidth: 0,
    },
    cardIcon: {
        flexShrink: 0,
        color: tokens.colorNeutralForeground3,
        marginTop: tokens.spacingVerticalXXS,
    },
    cardBody: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        minWidth: 0,
        flexGrow: 1,
    },
});
