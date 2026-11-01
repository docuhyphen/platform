import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationDetailsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        paddingTop: tokens.spacingVerticalXXL
    },

    loadingWrapper: {
        display: "flex",
        justifyContent: "center",
        padding: tokens.spacingHorizontalXL
    },

    errorMessage: {
        color: tokens.colorStatusDangerForeground1,
        padding: tokens.spacingHorizontalMNudge,
        marginBottom: tokens.spacingVerticalMNudge
    },

    orgNameSection: {
        marginBottom: tokens.spacingVerticalXL
    },

    registrationRow: {
        marginTop: tokens.spacingVerticalXS
    }
});
