import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDocumentSidebarStyles = makeStyles({
    sidebarContainer: {
        backgroundColor: "transparent",
        minWidth: "400px",
        minHeight: 0,
        height: "100%",
        display: "flex",
        flexDirection: "column",
    },
    /**
     * Mobile-only modifier: the sidebar promotes to a full-screen
     * OverlayDrawer, so it can't have a 400px min-width (small phones
     * are ~360px wide).
     */
    sidebarContainerMobile: {
        minWidth: 0,
        width: "100%",
    },
    drawerHeader: {
        paddingTop: "0",
    },
    drawerHeaderTitle: {
        width: "100%",
        minWidth: 0,
        "& .fui-DrawerHeaderTitle__heading": {
            width: "100%",
            minWidth: 0,
            flex: 1,
        },
        "& .fui-DrawerHeaderTitle__heading > *": {
            width: "100%",
            minWidth: 0,
        },
    },
    headerContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        width: "100%",
        minWidth: 0,
        boxSizing: "border-box",
    },
    commentFieldContainer: {
        position: "sticky",
        bottom: "0",
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalXS,
    },
    commentField: {
        flex: 1,
    }
});
