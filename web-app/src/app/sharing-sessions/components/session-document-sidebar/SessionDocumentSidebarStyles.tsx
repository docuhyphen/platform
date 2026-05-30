import {makeStyles, tokens} from "@fluentui/react-components";

export const useSessionDocumentSidebarStyles = makeStyles({
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
        gap: "8px",
        width: "100%",
        minWidth: 0,
        boxSizing: "border-box",
    },
    documentTitle: {
        wordBreak: "break-word",
    },
    documentTitleRow: {
        display: "flex",
        alignItems: "center",
        gap: "4px",
        minWidth: 0,
    },
    metadataToggleButton: {
        flexShrink: 0,
    },
    metadataPanel: {
        backgroundColor: tokens.colorNeutralBackground2,
        borderRadius: "6px",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        padding: "8px",
        overflow: "hidden",
        width: "100%",
        boxSizing: "border-box",
    },
    metadataPanelEntering: {
        animationName: {
            from: {
                opacity: 0,
                transform: "translateY(-4px)",
            },
            to: {
                opacity: 1,
                transform: "translateY(0)",
            },
        },
        animationDuration: "160ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },
    metadataPanelLeaving: {
        animationName: {
            from: {
                opacity: 1,
                transform: "translateY(0)",
            },
            to: {
                opacity: 0,
                transform: "translateY(-4px)",
            },
        },
        animationDuration: "160ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },
    metadataTable: {
        width: "100%",
        tableLayout: "fixed",
        boxSizing: "border-box",
    },
    metadataRow: {
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        ":last-child": {
            borderBottom: "none",
        },
    },
    metadataLabelCell: {
        width: "120px",
        padding: "4px 8px 4px 0",
    },
    metadataValueCell: {
        padding: "4px 0",
    },
    metadataLabel: {
        color: tokens.colorNeutralForeground3,
    },
    metadataValue: {
        color: tokens.colorNeutralForeground1,
        wordBreak: "break-word",
    },
    drawerBody: {
        flex: 1,
        minHeight: 0,
        overflow: "auto",
        ":last-child": {
            paddingBottom: "0",
        }
    },
    commentFieldContainer: {
        position: "sticky",
        bottom: "0",
        display: "flex",
        flexDirection: "row",
        gap: "4px",
    },
    commentField: {
        flex: 1,
    }
});