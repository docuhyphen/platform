import {makeStyles, tokens, typographyStyles,} from "@fluentui/react-components";

const useSharingSessionStyles = makeStyles({

    caption2: typographyStyles.caption2,
    caption1: typographyStyles.caption1,
    body1Strong: typographyStyles.body1Strong,

    emptyState: {
        display: "flex",
        width: "100%",
        height: "100%",
        flex: 1,
        justifyContent: "center",
        alignItems: "center",
        padding: "60px",
        boxSizing: "border-box"
    },

    skeletonRecipientEmail: {
        width: "150px",
    },

    skeletonSessionName: {
        flex: 1,
        marginRight: "10px",
    },

    skeletonCreatedDate: {
        width: "50px",
        marginRight: "10px",
    },

    skeletonSessionDescription: {
        flex: "1",
        marginRight: "10px",
    },

    truncatedText: {
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        maxWidth: "100%"
    },

    sessionName: {
        display: "block",
        flex: 1,
        minWidth: 0,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        maxWidth: "none",
    },

    sessionDescription: {
        display: "flex",
        alignItems: "center",
        minHeight: "18px",
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        maxWidth: "100%",
        width: "100%",
    },

    createdDate: {
        minWidth: "74px",
        display: "flex",
        justifyContent: "end",
        alignItems: "center",
        whiteSpace: "nowrap",
    },

    sharingSessionsListContainer: {
        width: "400px",
        minWidth: "400px",
        maxWidth: "400px",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        position: "relative",
        borderRadius: "4px",
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
        transition: "width 220ms ease, min-width 220ms ease, max-width 220ms ease",
    },

    sharingSessionsListContainerCollapsed: {

        width: "2.8rem",
        minWidth: "2.8rem",
        maxWidth: "2.8rem",
        overflow: "hidden",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        position: "relative",
        borderRadius: "4px",
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        transition: "width 220ms ease, min-width 220ms ease, max-width 220ms ease",
    },

    sharingSessionsListHeader: {
        background: tokens.colorNeutralBackground1,
        boxShadow: tokens.shadow4,
        width: "100%",
        height: "48px",
        padding: "8px",
        boxSizing: "border-box",
        display: "flex",
        gap: "8px",
    },

    filterSearchField: {
        flex: 1,
    },

    listCard: {
        width: "100%",
        padding: "8px",
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        boxSizing: "border-box"
    },

    listCardLastChild: {
        borderBottom: "none",
    },

    listCardHover: {
        transition: "all 0.1s ease",
        background: tokens.colorNeutralBackground1,
        borderLeft: "3px solid",
        borderLeftColor: tokens.colorBrandBackground,
    },

    sharingSessionsListFooter: {
        background: tokens.colorNeutralBackground1,
        padding: "8px",
        fontSize: "12px",
        width: "100%",
        boxSizing: "border-box",
        display: "flex",
        justifyContent: "space-between",
        marginTop: "auto",
        boxShadow: tokens.shadow4,
    },
    footerControls: {
        display: "flex"
    },

    sharingSessionsListBody: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        overflow: "auto",
        height: "100%",
        paddingTop: "2px",
        boxSizing: "border-box",
        scrollbarWidth: "thin",
        scrollbarColor: `${tokens.colorNeutralForeground3} ${tokens.colorNeutralBackground2}`,
        flex: 1,
        minWidth: "380px"
    },

    sharingSessionsListBodyWebkitScrollbar: {
        width: "12px",
    },

    sharingSessionsListBodyWebkitScrollbarTrack: {
        background: tokens.colorNeutralBackground2,
    },

    sharingSessionsListBodyWebkitScrollbarThumb: {
        backgroundColor: `${tokens.colorBrandForeground1}`,
        borderRadius: "10px",
        border: `3px solid ${tokens.colorNeutralBackground2}`,
    },

    sharingSessionsListBodyWebkitScrollbarThumbHover: {
        backgroundColor: `${tokens.colorBrandForeground1}`,
    },

    listCardItem: {
        display: "flex",
        flexDirection: "row",
        gap: "12px",
        boxSizing: "border-box",
        maxWidth: "100%"
    },

    listCardItemDetails: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        flex: 1,
        width: "calc(100% - 86px)"
    },

    listCardItemRow: {
        display: "flex",
        justifyContent: "space-between",
        gap: "4px",
        minWidth: 0,
    },

    titleMetaRow: {
        display: "flex",
        alignItems: "center",
        gap: "6px",
        whiteSpace: "nowrap",
        flexShrink: 0,
    },

    titleMetaDivider: {
        height: "14px",
        alignSelf: "center",
    },

    archiveStatusChipDeclined: {
        fontWeight: "600",
        minWidth: "68px",
        textAlign: "right",
        whiteSpace: "nowrap",
    },

    archiveStatusChipEnded: {
        fontWeight: "600",
        minWidth: "68px",
        textAlign: "right",
        whiteSpace: "nowrap",
        color: tokens.colorNeutralForeground4,
        borderTopColor: tokens.colorNeutralForeground4,
        borderRightColor: tokens.colorNeutralForeground4,
        borderBottomColor: tokens.colorNeutralForeground4,
        borderLeftColor: tokens.colorNeutralForeground4,
    },

    sharingSessionsListSelectedItem: {
        background: tokens.colorNeutralBackground2,
        borderLeft: "3px solid",
        transition: "all 0.1s ease",
        borderLeftColor: tokens.colorBrandForeground1,
    },

    sharingSessionsListItem: {
        "&:hover": {
            background: tokens.colorNeutralBackground1Hover,
            borderLeft: "3px solid",
            borderLeftColor: tokens.colorBrandForeground1,
            transition: "all 0.1s ease",
        },
    },

    inboxActions: {
        display: "flex",
        gap: "6px",
        marginTop: "6px",
        paddingTop: "6px",
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    tabsContainer: {
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
    },
});

export {useSharingSessionStyles};
export default useSharingSessionStyles;
