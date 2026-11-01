import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationTokenHintsStyles = makeStyles({
    tokenHintLabel: {
        color: tokens.colorNeutralForeground3,
        display: "block",
        marginBottom: tokens.spacingVerticalSNudge,
    },
    tokenHintRow: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
    },
    tokenHintMore: {
        color: tokens.colorNeutralForeground3,
    },
    tokenBadge: {
        fontFamily: "monospace",
        cursor: "pointer",
    },
});
