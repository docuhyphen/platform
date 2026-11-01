import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangesEmptyWorkspaceStyles = makeStyles({
    containerNoExchanges: {
        display: "flex",
        gap: tokens.spacingHorizontalXL,
        height: "100%",
        width: "100%",
        padding: `calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalM}) ${tokens.spacingHorizontalL} ${tokens.spacingVerticalL} ${tokens.spacingHorizontalL}`,
        boxSizing: "border-box",
        flexDirection: "column",
        justifyContent: "center",
        alignItems: "center",
        maxWidth: "640px",
        margin: "auto",
        textAlign: "center"
    },
    noExchangesIllustration: {
        width: "100%",
        maxWidth: "440px",
        height: "auto",
        color: tokens.colorBrandForeground1,
        marginBottom: tokens.spacingVerticalS
    },
});
