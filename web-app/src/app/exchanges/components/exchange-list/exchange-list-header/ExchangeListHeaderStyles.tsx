import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeListHeaderStyles = makeStyles({
    header: {
        background: tokens.colorNeutralBackground1,
        width: "100%",
        height: "48px",
        paddingTop: tokens.spacingVerticalS,
        paddingLeft: tokens.spacingVerticalS,
        paddingRight: tokens.spacingVerticalS,
        boxSizing: "border-box",
        display: "flex",
        gap: tokens.spacingHorizontalS,
        transitionProperty: "box-shadow",
        transitionDuration: "160ms",
        transitionTimingFunction: "ease",
    },
    headerElevated: {
        boxShadow: tokens.shadow4,
    },
});
