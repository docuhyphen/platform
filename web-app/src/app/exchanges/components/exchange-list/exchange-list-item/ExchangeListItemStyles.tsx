import {makeStyles, tokens, typographyStyles} from "@fluentui/react-components";

export const useExchangeListItemStyles = makeStyles({
    caption1: typographyStyles.caption1,

    truncatedText: {
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        maxWidth: "100%"
    },

    name: {
        display: "block",
        flex: 1,
        minWidth: 0,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        maxWidth: "none",
    },

    exchangeDescription: {
        display: "flex",
        alignItems: "center",
        minHeight: "18px",
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        maxWidth: "100%",
        width: "100%",
    },

    createdDate: {
        minWidth: "74px",
        display: "flex",
        justifyContent: "end",
        alignItems: "center",
        whiteSpace: "nowrap",
    },

    titleMetaRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalSNudge,
        whiteSpace: "nowrap",
        flexShrink: 0,
    },

    titleMetaDivider: {
        height: "14px",
        alignSelf: "center",
    },

    archiveStatusChipDeclined: {
        fontWeight: tokens.fontWeightSemibold,
        minWidth: "68px",
        textAlign: "right",
        whiteSpace: "nowrap",
    },

    archiveStatusChipEnded: {
        fontWeight: tokens.fontWeightSemibold,
        minWidth: "68px",
        textAlign: "right",
        whiteSpace: "nowrap",
        color: tokens.colorNeutralForeground4,
        borderTopColor: tokens.colorNeutralForeground4,
        borderRightColor: tokens.colorNeutralForeground4,
        borderBottomColor: tokens.colorNeutralForeground4,
        borderLeftColor: tokens.colorNeutralForeground4,
    },

    exchangesListItem: {
        "&:hover": {
            background: tokens.colorNeutralBackground1Hover,
            borderLeft: "3px solid",
            borderLeftColor: tokens.colorBrandForeground1,
            transition: "all 0.1s ease",
        },
    },

    exchangeListItemEnter: {
        opacity: 0,
        transform: "translateX(-18px)",
        animationName: {
            from: {
                opacity: 0,
                transform: "translateX(-18px)",
            },
            to: {
                opacity: 1,
                transform: "translateX(0)",
            },
        },
        animationDuration: "220ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
        animationFillMode: "both",
        willChange: "opacity, transform",
        "@media (prefers-reduced-motion: reduce)": {
            animationName: "none",
            animationDuration: "0ms",
            opacity: 1,
            transform: "none",
        },
    },
});
