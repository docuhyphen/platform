import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeListInboxRoleTabsStyles = makeStyles({
    tabList: {
        width: "100%",
    },
    tabContent: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
    },
});
