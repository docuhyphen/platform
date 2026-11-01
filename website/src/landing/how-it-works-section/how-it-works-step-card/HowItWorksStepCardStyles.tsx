import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE, CARD_RADIUS, SPACE_MD, SPACE_SM} from "../../shared.ts";

const illustrationBase = {
    width: "100%",
    aspectRatio: "1.2",
    backgroundRepeat: "no-repeat",
    backgroundPosition: "center",
    backgroundSize: "cover",
    backgroundColor: tokens.colorNeutralBackground1,
    borderRadius: tokens.borderRadiusXLarge,
    overflow: "hidden",
    transitionProperty: "filter, opacity",
    transitionDuration: tokens.durationNormal,
    transitionTimingFunction: tokens.curveEasyEase,
};

export const useHowItWorksStepCardStyles = makeStyles({
    stepCard: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: SPACE_SM,
        height: "100%",
        padding: SPACE_MD,
        backgroundColor: tokens.colorNeutralBackground1,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        transitionProperty: "background-color, color, border-color, box-shadow, transform",
        transitionDuration: tokens.durationNormal,
        transitionTimingFunction: tokens.curveEasyEase,
    },

    activeStepCard: {
        backgroundColor: tokens.colorBrandBackground,
        border: `${tokens.strokeWidthThin} solid transparent`,
        boxShadow: tokens.shadow8,
        transform: "translateY(-0.25rem)",
    },

    stepLabel: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.04em",
        textTransform: "uppercase",
    },

    stepTitle: {
        color: tokens.colorNeutralForeground1,
        fontWeight: tokens.fontWeightSemibold,

        [BREAKPOINT_MOBILE]: {
            fontSize: tokens.fontSizeBase600,
            lineHeight: tokens.lineHeightBase600,
        },
    },

    stepDescription: {
        color: tokens.colorNeutralForeground2,
    },

    activeStepText: {
        color: tokens.colorNeutralForegroundOnBrand,
    },

    activeIllustration: {
        filter: "brightness(0.96) saturate(1.05)",
    },

    illustrationOne: {
        ...illustrationBase,
        backgroundImage: "url('/illustrations/how-it-works-step-1.png')",
    },

    illustrationTwo: {
        ...illustrationBase,
        backgroundImage: "url('/illustrations/how-it-works-step-2.png')",
    },

    illustrationThree: {
        ...illustrationBase,
        backgroundImage: "url('/illustrations/how-it-works-step-3.png')",
    },

    illustrationFour: {
        ...illustrationBase,
        backgroundImage: "url('/illustrations/how-it-works-step-4.png')",
    },
});
