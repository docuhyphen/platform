import {makeStyles, tokens, typographyStyles,} from "@fluentui/react-components";

export const useSharingSessionStyles = makeStyles({
    caption2: typographyStyles.caption2,
    caption1: typographyStyles.caption1,
    body1Strong: typographyStyles.body1Strong,

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

    sharingSessionsListContainer: {
        width: "400px",
        height: "100%",
        overflow: "auto",
        marginLeft: "40px",
        border: "1px solid rgba(0, 0, 0, .1)",
        position: "relative",
        borderRadius: "4px",
        background: "white",
    },

    sharingSessionsListHeader: {
        background: "white",
        boxShadow: "1px 1px 0px 1px rgba(0, 0, 0, .1)",
        position: "absolute",
        width: "100%",
        height: "48px",
        top: "0",
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
        borderBottom: "1px solid rgba(0, 0, 0, .1)",
        height: "60px",
    },

    listCardLastChild: {
        borderBottom: "none",
    },

    listCardHover: {
        transition: "all 0.1s ease",
        background: "white",
        borderLeft: "3px solid #4b6496",
    },

    sharingSessionsListFooter: {
        position: "absolute",
        bottom: "0",
        background: "white",
        padding: "8px",
        fontSize: "12px",
        width: "100%",
        boxSizing: "border-box",
        display: "flex",
        justifyContent: "space-between",
        boxShadow: "-1px -1px 0 1px rgba(0, 0, 0, .1)",
    },

    sharingSessionsListBody: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        overflow: "auto",
        height: "100%",
        paddingTop: "56px",
        boxSizing: "border-box",
        paddingBottom: "36px",
        scrollbarWidth: "thin",
        scrollbarColor: "#888 #f1f1f1",
    },

    sharingSessionsListBodyWebkitScrollbar: {
        width: "12px",
    },

    sharingSessionsListBodyWebkitScrollbarTrack: {
        background: "#f1f1f1",
    },

    sharingSessionsListBodyWebkitScrollbarThumb: {
        backgroundColor: `${tokens.colorBrandForeground1}`,
        borderRadius: "10px",
        border: "3px solid #f1f1f1",
    },

    sharingSessionsListBodyWebkitScrollbarThumbHover: {
        backgroundColor: `${tokens.colorBrandForeground1}`,
    },

    listCardItem: {
        display: "flex",
        flexDirection: "row",
        gap: "12px",
    },

    listCardItemDetails: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        flex: 1,
    },

    listCardItemRow: {
        display: "flex",
        justifyContent: "space-between",
    },

    sharingSessionsListSelectedItem: {
        background: "white",
        borderLeft: "3px solid",
        borderLeftColor: tokens.colorBrandForeground1,
    },
});