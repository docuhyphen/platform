import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationTagEditorStyles = makeStyles({
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
