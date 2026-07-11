import {tokens, makeStyles} from "@fluentui/react-components";

export const useExchangeDocumentCommentComposerStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        width: "100%",
    },
    field: {
        alignItems: "start",
        paddingTop: tokens.spacingVerticalS,
        width: "100%",
    },
    input: {
        width: "100%",
        minHeight: "32px",
        transitionProperty: "min-height",
        transitionDuration: "160ms",
        transitionTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
        "& textarea": {
            minHeight: "32px",
            height: "32px",
            resize: "none",
            overflowY: "hidden",
            lineHeight: "20px",
            paddingTop: tokens.spacingVerticalXS,
            paddingBottom: tokens.spacingVerticalXS,
        },
        "&:focus-within textarea": {
            minHeight: "96px",
            height: "96px",
            overflowY: "auto",
        },
        "@media (prefers-reduced-motion: reduce)": {
            transitionDuration: "0ms",
        },
    },
    inputActive: {
        "& textarea": {
            minHeight: "96px",
            height: "96px",
            overflowY: "auto",
        },
    },
    actions: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
});
