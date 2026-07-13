import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
} from "../shared.ts";

export const useFeaturesSectionStyles = makeStyles({
    section: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXXL,
        padding: SECTION_PADDING_DESKTOP,
        scrollMarginTop: "5rem",

        [BREAKPOINT_MOBILE]: {
            gap: tokens.spacingVerticalXXL,
            padding: SECTION_PADDING_MOBILE,
        },
    },

});
