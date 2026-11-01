import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepTimelineItemStyles = makeStyles({
    stepCard: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: tokens.spacingHorizontalM,
    },
    stepHeader: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        marginBottom: tokens.spacingVerticalS,
    },
    metaText: {
        color: tokens.colorNeutralForeground3,
    },
    currentStepHighlight: {
        borderTopColor: tokens.colorBrandStroke1,
        borderRightColor: tokens.colorBrandStroke1,
        borderBottomColor: tokens.colorBrandStroke1,
        borderLeftColor: tokens.colorBrandStroke1,
    },
});
