import {makeStyles, tokens} from "@fluentui/react-components";

export const useIndustryExchangeTabsHeaderStyles = makeStyles({
    container: {
        display: "flex",
        alignItems: "center",
        minWidth: 0,
        backgroundColor: tokens.colorNeutralBackground1,
    },

    tabsViewport: {
        minWidth: 0,
        flex: 1,
        overflowX: "auto",
        overflowY: "hidden",
        scrollbarWidth: "none",

        "&::-webkit-scrollbar": {
            display: "none",
        },
    },
});
