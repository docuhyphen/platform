import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateItemActionsStyles = makeStyles({
    actions: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXXS,
        flexShrink: 0,
    },
});
