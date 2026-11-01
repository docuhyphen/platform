import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationPendingVerificationStyles = makeStyles({
    orgOnboardingContainer: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        maxWidth: "400px",
        gap: tokens.spacingHorizontalL
    },
    pendingInfoRow: {
        marginTop: tokens.spacingVerticalS
    }
});
