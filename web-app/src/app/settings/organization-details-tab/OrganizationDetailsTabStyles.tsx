import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationTabStyles = makeStyles({
    orgOnboardingContainer: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        maxWidth: "400px",
        gap: tokens.spacingHorizontalL
    },

    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        paddingTop: tokens.spacingVerticalXXL
    },

    dataContainer: {
        display: "flex",
        flexDirection: "row",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXXL
    },

    dataName: {
        minWidth: "200px"
    },

    dataEditable: {
        display: "flex",
        gap: tokens.spacingHorizontalS
    },

    mainDivider: {
        width: "100%",
        maxWidth: "300px"
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
    },

    pendingInfoRow: {
        marginTop: tokens.spacingVerticalS
    }
});
