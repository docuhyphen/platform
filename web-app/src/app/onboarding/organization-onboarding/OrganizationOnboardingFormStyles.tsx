import {tokens, makeStyles} from "@fluentui/react-components";

export const useOrganizationOnboardingForm = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        flex: 1,
    },
    dialogActions: {
        display: "flex",
        justifyContent: "end",
        marginTop: tokens.spacingVerticalL,
        gap: tokens.spacingHorizontalS
    }
});