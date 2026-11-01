import {makeStyles, tokens} from "@fluentui/react-components";

export const useReplacePrimaryRecipientActionStyles = makeStyles({
    replacementActions: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: tokens.spacingVerticalXS,
    },
    replacementActionRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        flexWrap: "wrap",
    },
    replacementInfoPopover: {
        maxWidth: "18rem",
    },
});
