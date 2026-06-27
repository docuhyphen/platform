import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        height: "100%",
        background: tokens.colorNeutralBackground2,
    },

    exchangeLoadingContainer: {
        display: "flex",
        width: "100%",
        height: "100%",
        justifyContent: "center",
        alignItems: "center",
    },

    exchangeContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        flex: 1,
        maxWidth: "680px",
        width: "100%",
        margin: "0 auto",
        marginTop: "48px",
        padding: "16px",
        boxSizing: "border-box",
    },

    exchangeDecisionContainer: {
        flex: 1,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "16px",
        boxSizing: "border-box",
    },

    name: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        background: tokens.colorNeutralBackground1,
        padding: "16px",
        boxShadow: tokens.shadow4,
        marginTop: "48px",
        borderRadius: tokens.borderRadiusMedium,
    }
})