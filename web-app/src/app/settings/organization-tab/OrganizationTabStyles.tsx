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
        height: "100%",
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
    tabsContainer: {
        flex: 1,
        overflow: "auto"
    }
});