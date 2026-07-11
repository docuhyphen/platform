import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeListTabsStyles = makeStyles({
    tabList: {
        padding: `0 ${tokens.spacingHorizontalS}`,
        width: "100%",
    },
    tabListCollapsed: {
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalXXS}`,
        width: "100%",
    },
    tabContent: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalSNudge,
    },
    tabContentCollapsed: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "100%",
    },
    collapsedIconWrapper: {
        position: "relative",
        display: "inline-flex",
    },
    collapsedIcon: {
        fontSize: "40px",
        lineHeight: 1,
    },
    collapsedBadge: {
        position: "absolute",
        top: "2px",
        right: "2px",
    },
});
