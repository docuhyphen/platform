import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationParingTabStyles = makeStyles({
    tabContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "16px"
    },
    header: {
        display: "flex",
        marginBottom: "16px",
        justifyContent: "space-between",
    },
});