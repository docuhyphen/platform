import {makeStyles, tokens} from "@fluentui/react-components";

export const useTrustedParticipantInvitationsStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingVerticalS,
        overflowY: "auto",
    },
    card: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        padding: tokens.spacingVerticalS,
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
});
