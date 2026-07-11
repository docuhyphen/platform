import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowMetadataFormStyles = makeStyles({
    formGrid: {
        display: "flex",
        gap: tokens.spacingVerticalS,
        flexDirection: "column"
    },

    formField: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },

    fullWidth: {
        gridColumn: "1 / -1",
    },

    triggerError: {
        color: tokens.colorStatusDangerForeground1,
    },

    triggerDescription: {
        color: tokens.colorNeutralForeground3,
    },

    tagInput: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
        alignItems: "center",
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
        minHeight: "32px",
        cursor: "text",
    },

    tagInputField: {
        border: "none",
        flexGrow: 1,
        minWidth: "8rem",
    },
});
