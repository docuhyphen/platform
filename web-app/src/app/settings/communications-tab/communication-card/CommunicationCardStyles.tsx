import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationCardStyles = makeStyles({
    commCard: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalS,
    },
    commCardContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        flex: "1",
        minWidth: 0,
    },
    summaryText: {
        color: tokens.colorNeutralForeground2,
    },
    subjectText: {
        color: tokens.colorNeutralForeground3,
        fontStyle: "italic",
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
    },
    badgeRow: {
        display: "flex",
        gap: tokens.spacingHorizontalSNudge,
        flexWrap: "wrap",
        alignItems: "center",
    },
});
