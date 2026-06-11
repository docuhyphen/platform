import {makeStyles, tokens, typographyStyles,} from "@fluentui/react-components";

const useExchangeStyles = makeStyles({

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

    skeletonExchangeName: {
        flex: 1,
        marginRight: "10px",
    },

    skeletonCreatedDate: {
        width: "50px",
        marginRight: "10px",
    },

    skeletonExchangeDescription: {
        flex: "1",
        marginRight: "10px",
    },

    truncatedText: {
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        maxWidth: "100%"
    },

    name: {
        display: "block",
        flex: 1,
        minWidth: 0,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        maxWidth: "none",
    },

    exchangeDescription: {
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

    exchangesListContainer: {
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
        // Include the 1px border in the width calc so `width: 100%` on
        // mobile doesn't render 2px wider than the parent (which was
        // causing horizontal overflow / a sliver of scrollable space).
        boxSizing: "border-box",
        transition: "width 220ms ease, min-width 220ms ease, max-width 220ms ease",
        // Phones: fill the available width. The desktop collapse/hover
        // mechanic isn't useful here because we swap panes entirely
        // (see ExchangesStyles containerMobileShowing* classes).
        "@media (max-width: 768px)": {
            width: "100%",
            minWidth: 0,
            maxWidth: "100%",
            flex: 1,
        },
    },

    exchangesListContainerCollapsed: {

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
        boxSizing: "border-box",
        transition: "width 220ms ease, min-width 220ms ease, max-width 220ms ease",
        // Collapsed sidebar is a desktop affordance: don't try to render
        // a 45px-wide column on a phone, expand back to full width.
        "@media (max-width: 768px)": {
            width: "100%",
            minWidth: 0,
            maxWidth: "100%",
            flex: 1,
        },
    },

    exchangesListHeader: {
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
        boxSizing: "border-box",
        // Allow flex children inside this card to shrink (the text rows
        // would otherwise force the card wider than the sidebar on
        // narrow viewports, producing the overflow scroll bar).
        minWidth: 0,
        overflow: "hidden",
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

    exchangesListFooter: {
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

    exchangesListBody: {
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
        minWidth: "380px",
        // Drop the 380px minWidth on phones, otherwise the list scrolls
        // horizontally inside a narrow viewport.
        "@media (max-width: 768px)": {
            minWidth: 0,
        },
    },

    exchangesListBodyWebkitScrollbar: {
        width: "12px",
    },

    exchangesListBodyWebkitScrollbarTrack: {
        background: tokens.colorNeutralBackground2,
    },

    exchangesListBodyWebkitScrollbarThumb: {
        backgroundColor: `${tokens.colorBrandForeground1}`,
        borderRadius: "10px",
        border: `3px solid ${tokens.colorNeutralBackground2}`,
    },

    exchangesListBodyWebkitScrollbarThumbHover: {
        backgroundColor: `${tokens.colorBrandForeground1}`,
    },

    listCardItem: {
        display: "flex",
        flexDirection: "row",
        gap: "12px",
        boxSizing: "border-box",
        maxWidth: "100%",
        // Without min-width: 0, the inner text rows (with whiteSpace:
        // nowrap) would expand the row past the sidebar's width on
        // narrow viewports instead of truncating with ellipsis.
        minWidth: 0,
    },

    listCardItemDetails: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        flex: 1,
        // calc(100% - 86px) was the original intent (avatar + gap budget),
        // but combined with a flex item that can't shrink it overflowed
        // narrow containers. Use flex: 1 + min-width: 0 so the column
        // takes whatever room the avatar leaves and lets text truncate.
        minWidth: 0,
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

    exchangesListSelectedItem: {
        background: tokens.colorNeutralBackground2,
        borderLeft: "3px solid",
        transition: "all 0.1s ease",
        borderLeftColor: tokens.colorBrandForeground1,
    },

    exchangesListItem: {
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

export {useExchangeStyles};
export default useExchangeStyles;
