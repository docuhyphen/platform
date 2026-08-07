import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeDocumentThumbnailStyles = makeStyles({
    thumbnailWrapper: {
        width: "100%",
        display: "flex",
        justifyContent: "center",
        boxSizing: "border-box",
        '@media (max-width: 640px)': {
            paddingLeft: tokens.spacingHorizontalXXL,
            paddingRight: tokens.spacingHorizontalXXL,
        },
        '@media (max-width: 390px)': {
            paddingLeft: tokens.spacingHorizontalXL,
            paddingRight: tokens.spacingHorizontalXL,
        },
    },
    thumbnailFrame: {
        width: "100%",
        maxWidth: "260px",
        aspectRatio: "210 / 297",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        overflow: "hidden",
        boxSizing: "border-box",
        backgroundColor: tokens.colorNeutralBackground3,
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke2),
        borderRadius: tokens.borderRadiusMedium,
        flexShrink: 0,
        '@media (max-width: 640px)': {
            maxWidth: "none",
        },
    },
    thumbnailSkeleton: {
        width: "100%",
        height: "100%",
    },
    thumbnailPlaceholderIcon: {
        fontSize: "48px",
        color: tokens.colorNeutralForeground4,
    },
    thumbnailImage: {
        display: "block",
        width: "100%",
        height: "100%",
        objectFit: "contain",
        boxShadow: tokens.shadow2,
    },
});

