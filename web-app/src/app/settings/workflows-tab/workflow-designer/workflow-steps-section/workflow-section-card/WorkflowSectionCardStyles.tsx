import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowSectionCardStyles = makeStyles({
    heading: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },
    icon: {
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeBase500,
        flexShrink: 0,
    },
    card: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        minWidth: 0,
        padding: tokens.spacingVerticalL,
        cursor: "pointer",
        transitionProperty: "background-color, border-color, box-shadow",
        transitionDuration: tokens.durationFaster,
        ":hover": {
            backgroundColor: tokens.colorNeutralBackground1Hover,
            borderTopColor: tokens.colorBrandStroke1,
            borderRightColor: tokens.colorBrandStroke1,
            borderBottomColor: tokens.colorBrandStroke1,
            borderLeftColor: tokens.colorBrandStroke1,
        },
        ":focus-visible": {
            outline: `2px solid ${tokens.colorStrokeFocus2}`,
            outlineOffset: "2px",
        },
    },
    description: {
        color: tokens.colorNeutralForeground3,
        overflowWrap: "anywhere",
    },
    summary: {
        color: tokens.colorBrandForeground1,
        overflowWrap: "anywhere",
    },
});
