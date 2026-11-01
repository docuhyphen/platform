import {makeStyles, tokens, webLightTheme} from "@fluentui/react-components";

const tokenPixels = (value: string, fallback: number): number =>
{
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : fallback;
};

export const WORKFLOW_GRAPH_EDGE_CORNER_RADIUS = tokenPixels(webLightTheme.borderRadiusXLarge, 8);

export const useWorkflowGraphEdgeStyles = makeStyles({
    label: {
        position: "absolute",
        transform: "translate(-50%, -50%) translate(var(--workflow-edge-label-x, 0px), var(--workflow-edge-label-y, 0px))",
        boxSizing: "border-box",
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        maxWidth: "160px",
        padding: `${tokens.spacingVerticalXXS} ${tokens.spacingHorizontalS}`,
        borderRadius: tokens.borderRadiusCircular,
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        backgroundColor: tokens.colorNeutralBackground1,
        color: tokens.colorNeutralForeground1,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        pointerEvents: "none",
    },
});
