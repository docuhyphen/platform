import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE, BUTTON_MIN_WIDTH, SPACE_LG, SPACE_SM, WIDTH_CONTENT} from "../shared.ts";

export const useFinalCtaSectionStyles = makeStyles({
    wrapper: {
        padding: `${tokens.spacingVerticalXXXL} ${tokens.spacingHorizontalM}`,
        backgroundImage: `linear-gradient(180deg, ${tokens.colorNeutralBackground1} 0%, ${tokens.colorBrandBackground2} 100%)`,
        borderTop: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },

    grid: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: SPACE_LG,
        alignItems: "center",
        maxWidth: WIDTH_CONTENT,
        marginLeft: "auto",
        marginRight: "auto",

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
        },
    },

    ctaColumn: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        textAlign: "left",
    },

    button: {
        minWidth: BUTTON_MIN_WIDTH,
    },
    buttonGroup: {
        marginTop: SPACE_SM,
    },
});

