import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateSettingsPanelStyles = makeStyles({
    panel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        maxWidth: "40rem",
    },
    problem: {
        color: tokens.colorPaletteRedForeground1,
    },
});
