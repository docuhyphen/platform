import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepSummaryCardStyles = makeStyles({
    card: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        padding: tokens.spacingVerticalM,
        cursor: "pointer",
        minWidth: 0,
        transitionProperty: "border-color, box-shadow, background-color",
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
    newCard: {
        backgroundColor: tokens.colorBrandBackground2,
        borderTopColor: tokens.colorBrandStroke1,
        borderRightColor: tokens.colorBrandStroke1,
        borderBottomColor: tokens.colorBrandStroke1,
        borderLeftColor: tokens.colorBrandStroke1,
        boxShadow: tokens.shadow4Brand,
    },
    firstRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        minWidth: 0,
    },
    typeBadge: {
        justifyContent: "flex-end",
    },
    stepIcon: {
        color: tokens.colorNeutralForeground3,
        flexShrink: 0,
    },
    stepNumber: {
        flex: 1,
        color: tokens.colorNeutralForeground3,
        flexShrink: 0,
    },
    name: {
        width: "100%",
        overflowWrap: "anywhere",
    },
    detail: {
        color: tokens.colorNeutralForeground3,
        overflowWrap: "anywhere",
    },
});
