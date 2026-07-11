import {tokens, makeStyles} from "@fluentui/react-components";

export const useTourCoachStyles = makeStyles({
    hiddenAnchor: {
        position: "fixed",
        top: "-9999px",
        left: "-9999px",
        width: "0",
        height: "0",
    },
    popoverSurface: {
        maxWidth: "340px",
    },
    backButtonRow: {
        paddingBottom: tokens.spacingVerticalMNudge,
        paddingInlineStart: tokens.spacingHorizontalL,
    },
});
