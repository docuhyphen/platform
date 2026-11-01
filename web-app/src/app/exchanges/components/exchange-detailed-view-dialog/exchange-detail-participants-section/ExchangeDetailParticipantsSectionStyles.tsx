import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailParticipantsSectionStyles = makeStyles({
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
