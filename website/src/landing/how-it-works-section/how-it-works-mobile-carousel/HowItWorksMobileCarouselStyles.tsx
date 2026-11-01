import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE, SPACE_MD, SPACE_SM} from "../../shared.ts";

export const useHowItWorksMobileCarouselStyles = makeStyles({
    mobileCarouselWrapper: {
        display: "none",

        [BREAKPOINT_MOBILE]: {
            display: "flex",
            flexDirection: "column",
            gap: SPACE_MD,
        },
    },

    mobileCarousel: {
        width: "100%",
    },

    mobileCarouselSlider: {
        width: "100%",
        gap: SPACE_SM,
    },

    mobileCarouselCard: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        width: "78%",
        minWidth: "78%",
        maxWidth: "78%",
    },

    mobileNavigation: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: SPACE_SM,
        width: "78%",
        maxWidth: "78%",
        margin: "0 auto",
    },

    mobileNavButton: {
        flex: "0 0 auto",
        minWidth: "unset",
    },

    mobileStepCount: {
        color: tokens.colorNeutralForeground2,
        fontWeight: tokens.fontWeightSemibold,
        whiteSpace: "nowrap",
    },
});
