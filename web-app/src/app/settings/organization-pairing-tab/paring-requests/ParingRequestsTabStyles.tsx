import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationParingTabStyles = makeStyles({
    tabContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXXL,
        width: "100%",
        minWidth: 0,
        // Tables inside this section can be wide; let them scroll
        // horizontally rather than expanding the parent settings card.
        overflowX: "auto",
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
    },
    tabListContainer: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalXXXL
    },
    tabs: {
        flexGrow: "1"
    },
    acceptButtonIcon: {
        color: tokens.colorBrandForeground1
    },
    divider: {
        width: "300px",
        maxWidth: "100%",
        "@media (max-width: 768px)": {
            width: "100%",
        },
    }
});