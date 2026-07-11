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
    row: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXXS,
    },
    labelValueRow: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalS,
        alignItems: "flex-start",
        flexWrap: "wrap",
    },
    label: {
        color: tokens.colorNeutralForeground3,
        minWidth: "120px",
        flexShrink: 0,
    },
    settingsGrid: {
        display: "flex",
        flexDirection: "column",
        gridTemplateColumns: "1fr 1fr",
        gap: tokens.spacingHorizontalS,
    },
    settingItem: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalSNudge,
        alignItems: "center",
    },
    descriptionText: {
        whiteSpace: "pre-wrap",
        color: tokens.colorNeutralForeground1,
    },
    noDescription: {
        color: tokens.colorNeutralForeground4,
        fontStyle: "italic",
    },
    participantRow: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXXS,
        padding: `${tokens.spacingVerticalSNudge} 0`,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        ":last-child": {
            borderBottom: "none",
        },
    },
    participantEmail: {
        color: tokens.colorNeutralForeground2,
    },
    statusBadge: {
        alignSelf: "flex-start",
    },
    additionalParticipantsContainer: {
        marginTop: tokens.spacingVerticalXS,
    },
    additionalParticipantsLabel: {
        color: tokens.colorNeutralForeground3,
    },
    initialShareMessageText: {
        marginTop: tokens.spacingVerticalSNudge,
        fontStyle: "italic",
    },
});
