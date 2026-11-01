import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationSubTabsStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        height: "100%",
        minHeight: 0,
    },
    tabListWrapper: {
        background: tokens.colorNeutralBackground1,
        paddingBottom: tokens.spacingVerticalXS,
        marginBottom: tokens.spacingVerticalXS,
        flexShrink: 0,
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },
    tabsContainer: {
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },
});
