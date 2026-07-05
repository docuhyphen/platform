import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowMetadataFormStyles = makeStyles({
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

    triggerError: {
        color: tokens.colorStatusDangerForeground1,
    },

    triggerDescription: {
        color: "var(--colorNeutralForeground3)",
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

    tagInputField: {
        border: "none",
        flexGrow: 1,
        minWidth: "8rem",
    },
});
