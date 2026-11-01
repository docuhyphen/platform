import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeListFooterStyles = makeStyles({
    footer: {
        background: tokens.colorNeutralBackground1,
        padding: tokens.spacingHorizontalS,
        fontSize: tokens.fontSizeBase200,
        width: "100%",
        boxSizing: "border-box",
        display: "flex",
        justifyContent: "space-between",
        marginTop: "auto",
        transitionProperty: "box-shadow",
        transitionDuration: "160ms",
        transitionTimingFunction: "ease",
    },
    footerElevated: {
        boxShadow: tokens.shadow4,
    },
});
