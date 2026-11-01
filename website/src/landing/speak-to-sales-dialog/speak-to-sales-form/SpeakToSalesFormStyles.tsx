import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE, SPACE_MD, SPACE_SM} from "../../shared.ts";

export const useSpeakToSalesFormStyles = makeStyles({
    form: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_MD,
        marginTop: SPACE_SM,
    },

    row: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: SPACE_MD,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
        },
    },

    sectionLabel: {
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.06em",
        textTransform: "uppercase",
    },

    divider: {
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },

    honeypot: {
        position: "absolute",
        width: "1px",
        height: "1px",
        overflow: "hidden",
        left: "-9999px",
        top: "auto",
    },
});
