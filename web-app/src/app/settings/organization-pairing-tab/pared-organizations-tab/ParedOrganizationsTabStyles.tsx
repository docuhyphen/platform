import {tokens, makeStyles} from "@fluentui/react-components";

export const useOrganizationParingTabStyles = makeStyles({
    tabContainer: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalL,
        flexWrap: "wrap",
    },
    header: {
        display: "flex",
        marginBottom: tokens.spacingVerticalL,
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    container: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        minWidth: 0,
        // Wide tables scroll horizontally inside their own container on
        // small screens so they don't stretch the settings card.
        overflowX: "auto",
    },
    tabListContainer: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalXXXL
    },
    tabs: {
        flexGrow: "1"
    }
});