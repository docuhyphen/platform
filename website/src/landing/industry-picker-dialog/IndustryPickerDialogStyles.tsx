import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE, CARD_RADIUS, SPACE_MD, SPACE_SM, SPACE_XS} from "../shared.ts";

export const useIndustryPickerDialogStyles = makeStyles({
    surface: {
        width: "100%",
        maxWidth: "30rem",
        borderRadius: CARD_RADIUS,
        padding: tokens.spacingHorizontalXXXL,

        [BREAKPOINT_MOBILE]: {
            padding: tokens.spacingHorizontalXL,
        },
    },

    title: {
        fontSize: tokens.fontSizeBase500,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorBrandForeground1,
    },

    subtitle: {
        color: tokens.colorNeutralForeground2,
    },

    grid: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: SPACE_SM,
        marginTop: SPACE_MD,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            gap: SPACE_XS,
        },
    },

    option: {
        justifyContent: "flex-start",
        textAlign: "left",
        minHeight: "2.8rem",
        fontWeight: tokens.fontWeightRegular,
    },

    actions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: SPACE_SM,
        paddingTop: SPACE_MD,
    },
});
