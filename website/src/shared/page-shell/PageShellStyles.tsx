import {makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    HEADER_HEIGHT,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    WIDTH_CONTENT,
} from "../../landing/shared.ts";

export const usePageShellStyles = makeStyles({
    page: {
        position: "relative",
        display: "flex",
        flexDirection: "column",
        minHeight: "100vh",
        width: "100%",
        backgroundColor: tokens.colorNeutralBackground2,
    },

    main: {
        flex: 1,
        width: "100%",
        paddingTop: HEADER_HEIGHT,
        boxSizing: "border-box",
    },

    content: {
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },
});
