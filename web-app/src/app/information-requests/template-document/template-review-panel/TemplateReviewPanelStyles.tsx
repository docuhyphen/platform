import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateReviewPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
    },
    ordering: {
        maxWidth: "24rem",
    },
    muted: {
        color: tokens.colorNeutralForeground3,
    },
    problem: {
        color: tokens.colorPaletteRedForeground1,
    },
});
