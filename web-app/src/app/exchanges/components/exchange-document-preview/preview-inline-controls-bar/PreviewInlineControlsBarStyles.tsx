import {makeStyles, tokens} from '@fluentui/react-components';

export const usePreviewInlineControlsBarStyles = makeStyles({
    inlineControlsBar: {
        position: "absolute",
        bottom: 0,
        left: "50%",
        transform: "translateX(-50%)",
        marginBottom: tokens.spacingVerticalM,
        zIndex: 2,
        display: "inline-flex",
        flexWrap: "nowrap",
        overflowX: "auto",
        scrollbarWidth: "none",
        "&::-webkit-scrollbar": {
            display: "none",
        },
        justifyContent: "center",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        width: "fit-content",
        maxWidth: "calc(100% - 32px)",
        boxSizing: "border-box",
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalM}`,
        backgroundColor: tokens.colorNeutralBackground1,
        borderRadius: tokens.borderRadiusCircular,
        boxShadow: tokens.shadow8,
        "& > *": {
            flexShrink: 0,
        },
        opacity: 1,
        transitionProperty: "opacity",
        transitionDuration: "160ms",
        transitionTimingFunction: "ease",
        ":hover": {
            opacity: 1,
        },
        ":focus-within": {
            opacity: 1,
        },
    },

    hiddenControl: {
        display: "none",
    },
});
