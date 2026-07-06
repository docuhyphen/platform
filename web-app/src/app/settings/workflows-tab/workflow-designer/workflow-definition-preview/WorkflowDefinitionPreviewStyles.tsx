import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowDefinitionPreviewStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "0.75rem",
    },
    containerFillHeight: {
        flex: 1,
        minHeight: 0,
    },

    applicabilitySummary: {
        display: "flex",
        flexDirection: "column",
        gap: "2px",
        padding: "0.5rem 0.75rem",
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    applicabilityDetail: {
        color: "var(--colorNeutralForeground3)",
    },
});
