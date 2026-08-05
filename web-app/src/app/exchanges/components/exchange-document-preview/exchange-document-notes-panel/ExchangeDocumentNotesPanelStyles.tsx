import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDocumentNotesPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        flexShrink: 0,
        width: "360px",
        maxWidth: "40%",
        minWidth: 0,
        minHeight: 0,
        overflow: "hidden",
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        boxSizing: "border-box",
        "@media (max-width: 768px)": {
            width: "100%",
            maxWidth: "100%",
            minHeight: "40%",
        },
    },
    header: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        flexShrink: 0,
    },
    content: {
        flex: 1,
        minHeight: 0,
        padding: `0 ${tokens.spacingHorizontalS} ${tokens.spacingVerticalS}`,
        boxSizing: "border-box",
    },
});
