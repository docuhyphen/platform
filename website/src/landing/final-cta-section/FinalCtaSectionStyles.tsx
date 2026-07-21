import {makeStyles, tokens} from "@fluentui/react-components";
import {BUTTON_MIN_WIDTH, SPACE_SM} from "../shared.ts";

export const useFinalCtaSectionStyles = makeStyles({
    wrapper: {
        textAlign: "center",
        padding: `${tokens.spacingVerticalXXXL} ${tokens.spacingHorizontalM}`,
        backgroundImage: `linear-gradient(180deg, ${tokens.colorNeutralBackground1} 0%, ${tokens.colorBrandBackground2} 100%)`,
        borderTop: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },
    button: {
        minWidth: BUTTON_MIN_WIDTH,
    },
    buttonGroup: {
        marginTop: SPACE_SM,
    },
});

