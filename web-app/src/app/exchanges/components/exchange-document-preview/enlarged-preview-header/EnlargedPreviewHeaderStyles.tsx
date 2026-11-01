import {makeStyles, tokens} from '@fluentui/react-components';

export const useEnlargedPreviewHeaderStyles = makeStyles({
    documentName: {
        display: "flex",
        flexDirection: "column",
        minWidth: 0,
        "@media (max-width: 768px)": {
            alignItems: "center",
            textAlign: "center",
            width: "100%",
        },
    },

    enlargedPreviewHeader: {
        zIndex: 9999,
        margin: "0 auto",
        boxSizing: "border-box",
        background: tokens.colorNeutralBackground1,
        width: "100%",
        display: "flex",
        justifyContent: "space-between",
        padding: tokens.spacingHorizontalL,
        borderRadius: tokens.borderRadiusMedium,
        animationName: {
            from: {
                opacity: 0,
                transform: "translateY(-8px)",
            },
            to: {
                opacity: 1,
                transform: "translateY(0)",
            },
        },
        animationDuration: "220ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
        "@media (max-width: 768px)": {
            flexDirection: "column",
            alignItems: "center",
            gap: tokens.spacingHorizontalS,
            padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalM}`,
        },
    },

    enlargedPreviewHeaderClosing: {
        animationName: {
            from: {
                opacity: 1,
                transform: "translateY(0)",
            },
            to: {
                opacity: 0,
                transform: "translateY(-8px)",
            },
        },
        animationDuration: "180ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },

    enlargedPreviewHeaderActions: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        flexWrap: "wrap",
        justifyContent: "center",
        maxWidth: "100%",
        minWidth: 0,
    },

    dividerFullHeight: {
        height: "100%",
    },
});
