import {makeStyles, tokens} from '@fluentui/react-components';

export const usePreviewWatermarkStyles = makeStyles({
    watermarkOverlay: {
        position: "absolute",
        inset: 0,
        pointerEvents: "none",
        overflow: "hidden",
        zIndex: 5,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        userSelect: "none",
    },

    watermarkText: {
        transform: "rotate(-30deg)",
        opacity: "0.15",
        fontSize: tokens.fontSizeHero1000,
        fontWeight: tokens.fontWeightBold,
        color: tokens.colorNeutralForeground1,
        whiteSpace: "nowrap",
        letterSpacing: "0.2em",
    },
});
