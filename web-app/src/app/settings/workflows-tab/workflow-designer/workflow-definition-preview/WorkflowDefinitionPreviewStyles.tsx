import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowDefinitionPreviewStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },
    containerFillHeight: {
        flex: 1,
        minHeight: 0,
    },

    applicabilitySummary: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXXS,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    applicabilityDetail: {
        color: tokens.colorNeutralForeground3,
    },
});
