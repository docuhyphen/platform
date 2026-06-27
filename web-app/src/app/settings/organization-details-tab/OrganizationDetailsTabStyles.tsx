import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationTabStyles = makeStyles({
    orgOnboardingContainer: {
        display: "flex",
        flexDirection: "column",
        width: "400px",
        gap: "16px"
    },

    container: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        paddingTop: "1.5rem"
    },

    dataContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "24px"
    },

    dataName: {
        minWidth: "200px"
    },

    dataEditable: {
        display: "flex",
        gap: "8px"
    },

    mainDivider: {
        width: "300px"
    },

    loadingWrapper: {
        display: "flex",
        justifyContent: "center",
        padding: "20px"
    },

    errorMessage: {
        color: tokens.colorStatusDangerForeground1,
        padding: "10px",
        marginBottom: "10px"
    },

    orgNameSection: {
        marginBottom: "20px"
    },

    registrationRow: {
        marginTop: "5px"
    },

    pendingInfoRow: {
        marginTop: "8px"
    }
});