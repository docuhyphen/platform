import {makeStyles, tokens} from "@fluentui/react-components";

/**
 * Edge label pill styling. The label is rendered as an HTML element (via
 * React Flow's `EdgeLabelRenderer`) rather than raw SVG text, so its
 * background always sizes itself to the real, rendered text via normal CSS
 * box layout instead of a one-shot `getBBox()` measurement that can go stale
 * and leave the text overflowing its background box.
 */
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
