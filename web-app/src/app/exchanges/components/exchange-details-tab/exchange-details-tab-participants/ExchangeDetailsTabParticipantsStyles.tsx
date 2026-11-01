import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailsTabParticipantsStyles = makeStyles({
    row: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXXS,
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
    additionalParticipantsContainer: {
        marginTop: tokens.spacingVerticalXS,
    },
    additionalParticipantsLabel: {
        color: tokens.colorNeutralForeground3,
    },
});
