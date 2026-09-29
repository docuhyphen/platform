import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateVersionsPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        maxWidth: "44rem",
    },
    muted: {
        color: tokens.colorNeutralForeground3,
    },
    actions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        paddingTop: tokens.spacingVerticalS,
    },
});
