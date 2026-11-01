import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        padding: `${tokens.spacingVerticalS} 0`,
        overflowY: "auto",
        flex: 1,
        minHeight: 0,
    },
    section: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
    },
    sectionTitle: {
        color: tokens.colorNeutralForeground3,
        textTransform: "uppercase",
        letterSpacing: "0.04em",
    },
    descriptionText: {
        whiteSpace: "pre-wrap",
        color: tokens.colorNeutralForeground1,
    },
    noDescription: {
        color: tokens.colorNeutralForeground4,
        fontStyle: "italic",
    },
    statusBadge: {
        alignSelf: "flex-start",
    },
    initialShareMessageText: {
        marginTop: tokens.spacingVerticalSNudge,
        fontStyle: "italic",
    },
});
