import {makeStyles, tokens} from "@fluentui/react-components";

export const useCarouselStyles = makeStyles({
    carousel: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        width: "100%",
        height: "100%",
        overflow: "hidden",
    },
    carouselInner: {
        display: "block",
        maxWidth: "100%",
        width: "100%",
    },
    carouselItem: {
        display: "none",
        boxSizing: "border-box",
        padding: tokens.spacingHorizontalXL,
        textAlign: "center",
    },
    carouselItemActive: {
        display: "block",
    },
    carouselDots: {
        display: "flex",
        gap: tokens.spacingHorizontalXS,
        marginTop: tokens.spacingVerticalMNudge,
    },
    dot: {
        width: "10px",
        height: "10px",
        backgroundColor: tokens.colorNeutralBackgroundAlpha,
        borderRadius: tokens.borderRadiusCircular,
        cursor: "pointer",
        transition: "background-color 0.15s ease",
    },
    dotActive: {
        backgroundColor: tokens.colorNeutralForegroundOnBrand,
    },
});
