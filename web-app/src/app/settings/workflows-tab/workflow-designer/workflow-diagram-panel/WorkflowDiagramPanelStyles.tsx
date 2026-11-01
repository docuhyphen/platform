import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowDiagramPanelStyles = makeStyles({
    splitDiagramColumn: {
        flex: "0 0 60%",
        height: "100%",
        minWidth: 0,
        minHeight: 0,
        display: "flex",
        flexDirection: "column",
        "@media (max-width: 1100px)": {
            flex: "1 1 auto",
            width: "100%",
            height: "auto",
        },
    },
    diagramPanel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXL,
        flex: 1,
        height: "100%",
        minHeight: 0,
    },
    previewLoading: {
        display: "flex",
        justifyContent: "center",
        padding: tokens.spacingHorizontalXXXL,
    },
});
