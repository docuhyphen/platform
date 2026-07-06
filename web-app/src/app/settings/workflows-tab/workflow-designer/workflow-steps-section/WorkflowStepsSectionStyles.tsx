import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowStepsSectionStyles = makeStyles({
    stepDetail: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
    },

    stepList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },

    stepListHeader: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        position: "sticky",
        top: 0,
        zIndex: 2,
        paddingBlock: tokens.spacingVerticalS,
        backgroundColor: tokens.colorNeutralBackground2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        paddingInline: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },

    headerTitle: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },

    noStepsText: {
        color: "var(--colorNeutralForeground3)",
    },
});
