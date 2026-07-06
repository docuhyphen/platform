import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowGraphPreviewStyles = makeStyles({
    preview: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        width: "100%",
    },
    previewFillHeight: {
        flex: 1,
        minHeight: 0,
    },
    loading: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "100%",
        minHeight: "240px",
    },
});
