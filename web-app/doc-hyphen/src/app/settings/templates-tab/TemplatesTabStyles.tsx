import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplatesTabStyles = makeStyles({
    tabContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "16px",
        width: "100%",
    },
    header: {
        width: "100%",
        display: "flex",
        marginBottom: "16px",
        justifyContent: "space-between",
    },
});