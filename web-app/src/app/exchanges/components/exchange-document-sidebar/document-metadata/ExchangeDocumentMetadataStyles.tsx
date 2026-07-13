import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDocumentMetadataStyles = makeStyles({
    title: {wordBreak: "break-word"},
    titleRow: {display: "flex", alignItems: "center", gap: tokens.spacingHorizontalXS, minWidth: 0},
    toggleButton: {flexShrink: 0},
    panel: {
        backgroundColor: tokens.colorNeutralBackground2,
        borderRadius: tokens.borderRadiusLarge,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        padding: tokens.spacingHorizontalS,
        overflow: "hidden",
        width: "100%",
        boxSizing: "border-box",
    },
    panelEntering: {
        animationName: {
            from: {opacity: 0, transform: "translateY(-4px)"},
            to: {opacity: 1, transform: "translateY(0)"},
        },
        animationDuration: "160ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },
    panelLeaving: {
        animationName: {
            from: {opacity: 1, transform: "translateY(0)"},
            to: {opacity: 0, transform: "translateY(-4px)"},
        },
        animationDuration: "160ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },
    table: {width: "100%", tableLayout: "fixed", boxSizing: "border-box"},
    row: {borderBottom: `1px solid ${tokens.colorNeutralStroke2}`, ":last-child": {borderBottom: "none"}},
    labelCell: {width: "120px", padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS} ${tokens.spacingVerticalXS} 0`},
    valueCell: {padding: `${tokens.spacingVerticalXS} 0`},
    label: {color: tokens.colorNeutralForeground3},
    value: {color: tokens.colorNeutralForeground1, wordBreak: "break-word"},
});
