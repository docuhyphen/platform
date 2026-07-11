import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDDetailedViewDialogStyles = makeStyles({
    dialogSurface: {
        width: "min(760px, 92vw)",
        maxHeight: "85vh",
    },

    dialogContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        maxHeight: "68vh",
        overflowY: "auto",
        paddingRight: tokens.spacingHorizontalXS,
    },

    sectionCard: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: tokens.spacingHorizontalM,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
    },

    titleRow: {
        display: "grid",
        gridTemplateColumns: "1fr auto",
        alignItems: "start",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },

    exchangeTitleText: {
        minWidth: 0,
        whiteSpace: "normal",
        overflowWrap: "anywhere",
        wordBreak: "break-word",
    },

    statusChip: {
        whiteSpace: "nowrap",
        alignSelf: "start",
    },

    keyValueGrid: {
        display: "grid",
        gridTemplateColumns: "minmax(180px, 240px) 1fr",
        columnGap: tokens.spacingHorizontalM,
        rowGap: tokens.spacingVerticalSNudge,
    },

    keyLabel: {
        color: tokens.colorNeutralForeground3,
    },

    additionalParticipantsContainer: {
        marginTop: tokens.spacingVerticalS,
    },

    additionalParticipantsLabel: {
        display: "block",
        marginBottom: tokens.spacingVerticalXS,
    },

    participantRow: {
        marginBottom: tokens.spacingVerticalXXS,
    },
});