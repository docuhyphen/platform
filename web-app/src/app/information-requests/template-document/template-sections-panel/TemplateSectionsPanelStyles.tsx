import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateSectionsPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
    },
    muted: {
        color: tokens.colorNeutralForeground3,
    },
    addButton: {
        alignSelf: "flex-start",
    },
});
