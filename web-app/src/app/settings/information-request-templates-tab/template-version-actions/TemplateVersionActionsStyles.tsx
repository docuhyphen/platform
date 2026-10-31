import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateVersionActionsStyles = makeStyles({
    actions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        paddingTop: tokens.spacingVerticalS,
    },
});
