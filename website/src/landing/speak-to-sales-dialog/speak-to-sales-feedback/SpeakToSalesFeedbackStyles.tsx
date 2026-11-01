import {makeStyles, tokens} from "@fluentui/react-components";
import {SPACE_MD} from "../../shared.ts";

export const useSpeakToSalesFeedbackStyles = makeStyles({
    feedback: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: SPACE_MD,
        padding: `${tokens.spacingVerticalXXXL} 0`,
        textAlign: "center",
    },

    feedbackIcon: {
        fontSize: tokens.fontSizeHero900,
    },

    successIcon: {
        color: tokens.colorPaletteGreenForeground1,
    },

    errorIcon: {
        color: tokens.colorPaletteRedForeground1,
    },
});
