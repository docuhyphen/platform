import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeStyles = makeStyles({
    themeProvider: {
        width: "100%",
        height: "100%",
    },

    container: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        height: "100%",
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        backgroundColor: tokens.colorNeutralBackground2,
    },

    exchangeLoadingContainer: {
        display: "flex",
        width: "100%",
        minHeight: "70vh",
        justifyContent: "center",
        alignItems: "center",
    },

    exchangeDecisionContainer: {
        flex: 1,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: `calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXXXL}) ${tokens.spacingHorizontalL} ${tokens.spacingVerticalXXXL}`,
        boxSizing: "border-box",
    },

});
