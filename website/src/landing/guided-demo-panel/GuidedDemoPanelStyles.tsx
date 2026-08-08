import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE, CARD_RADIUS, SPACE_LG, SPACE_MD, SPACE_SM} from "../shared.ts";

export const useGuidedDemoPanelStyles = makeStyles({
    panel: {
        backgroundColor: tokens.colorNeutralBackground1,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        textAlign: "left",
        height: "100%",
        boxSizing: "border-box",
        justifyContent: "center",
    },

    actions: {
        display: "flex",
        gap: SPACE_MD,
        flexWrap: "wrap",
        marginTop: SPACE_SM,

        [BREAKPOINT_MOBILE]: {
            gap: SPACE_SM,
        },
    },
});

