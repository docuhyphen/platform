import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "1rem",
        padding: "8px 0",
        overflowY: "auto",
        flex: 1,
        minHeight: 0,
    },
    section: {
        display: "flex",
        flexDirection: "column",
        gap: "6px",
    },
    sectionTitle: {
        color: tokens.colorNeutralForeground3,
        textTransform: "uppercase",
        letterSpacing: "0.04em",
    },
    row: {
        display: "flex",
        flexDirection: "column",
        gap: "2px",
    },
    labelValueRow: {
        display: "flex",
        flexDirection: "row",
        gap: "8px",
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
        gap: "8px",
    },
    settingItem: {
        display: "flex",
        flexDirection: "row",
        gap: "6px",
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
        gap: "2px",
        padding: "6px 0",
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
        marginTop: "4px",
    },
    additionalParticipantsLabel: {
        color: tokens.colorNeutralForeground3,
    },
    maxViewsContainer: {
        marginTop: "8px",
    },
    initialShareMessageText: {
        marginTop: "6px",
        fontStyle: "italic",
    },
});
