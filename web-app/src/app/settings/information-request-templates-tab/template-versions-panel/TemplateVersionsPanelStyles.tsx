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
    hash: {
        color: tokens.colorNeutralForeground3,
        fontFamily: tokens.fontFamilyMonospace,
        overflowWrap: "anywhere",
    },
    choice: {
        maxWidth: "16rem",
    },
});
