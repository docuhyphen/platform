import {makeStyles} from "@fluentui/react-components";

export const useOrganizationParingTabStyles = makeStyles({
    tabContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "16px",
        flexWrap: "wrap",
    },
    header: {
        display: "flex",
        marginBottom: "16px",
        justifyContent: "end",
    },
    container: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        minWidth: 0,
    },
    // The inner TabList is rendered `vertical` for desktop so it sits as
    // a sidebar next to the tab content. On phones a vertical sidebar
    // eats too much horizontal space, so collapse the layout into a
    // single column where the TabList becomes a horizontal scroll strip
    // above the content.
    tabListContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "36px",
        width: "100%",
        minWidth: 0,
        "@media (max-width: 768px)": {
            flexDirection: "column",
            gap: "12px",
        },
    },
    tabs: {
        flexGrow: "1",
        minWidth: 0,
        width: "100%",
        overflowX: "auto",
    }
});