import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepCommunicationRowStyles = makeStyles({
    notificationRow: {
        display: "flex",
        gap: tokens.spacingHorizontalSNudge,
        alignItems: "center",
        flexWrap: "wrap",
    },

    notificationHint: {
        color: tokens.colorNeutralForeground3,
    },
});
