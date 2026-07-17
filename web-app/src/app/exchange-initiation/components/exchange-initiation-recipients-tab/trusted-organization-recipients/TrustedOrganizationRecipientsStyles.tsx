import {makeStyles, tokens} from "@fluentui/react-components";

export const useTrustedOrganizationRecipientsStyles = makeStyles({
    container: {
        display: "grid",
        gap: tokens.spacingVerticalM,
        width: "100%",
    },
    verification: {
        color: tokens.colorNeutralForeground2,
    },
    resolver: {
        display: "grid",
        gap: tokens.spacingVerticalS,
        justifyItems: "start",
        width: "100%",
    },
    confirmationCard: {
        width: "100%",
        gap: tokens.spacingVerticalXS,
    },
    verificationState: {
        display: "grid",
        gap: tokens.spacingVerticalXS,
        justifyItems: "start",
    },
});
