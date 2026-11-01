import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeEmptyDetailsFrameStyles = makeStyles({
    noExchangeSelectedSection: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "100%",
        width: "100%",
    },
    inboxEmptyDetailsContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        alignItems: "center",
        maxWidth: "520px",
        textAlign: "center",
        padding: tokens.spacingHorizontalL
    },
    inboxEmptyIllustration: {
        width: "100%",
        maxWidth: "340px",
        height: "auto",
        color: tokens.colorBrandForeground1
    },
});
