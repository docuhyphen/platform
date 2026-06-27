import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowDesignerStyles = makeStyles({
    helpButton: {
        marginLeft: "auto",
    },

    triggerError: {
        color: tokens.colorStatusDangerForeground1,
    },

    triggerDescription: {
        color: "var(--colorNeutralForeground3)",
    },

    tagInputField: {
        border: "none",
        flexGrow: 1,
        minWidth: "8rem",
    },

    noStepsText: {
        color: "var(--colorNeutralForeground3)",
    },

    container: {
        display: "flex",
        flexDirection: "column",
        gap: "1.25rem",
    },

    topBar: {
        display: "flex",
        alignItems: "center",
        gap: "0.75rem",
        marginBottom: "0.25rem",
    },

    formGrid: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: "1rem",
        "@media (max-width: 768px)": {
            gridTemplateColumns: "1fr",
        },
    },

    formField: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
    },

    fullWidth: {
        gridColumn: "1 / -1",
    },

    stepList: {
        display: "flex",
        flexDirection: "column",
        gap: "0.75rem",
    },

    stepListHeader: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        marginBottom: "0.25rem",
    },

    saveBar: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        gap: "0.5rem",
        paddingTop: "1rem",
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    portabilityWarning: {
        flex: 1,
        color: tokens.colorStatusWarningForeground1,
    },

    tagInput: {
        display: "flex",
        flexWrap: "wrap",
        gap: "4px",
        alignItems: "center",
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: "4px 8px",
        minHeight: "32px",
        cursor: "text",
    },
});

