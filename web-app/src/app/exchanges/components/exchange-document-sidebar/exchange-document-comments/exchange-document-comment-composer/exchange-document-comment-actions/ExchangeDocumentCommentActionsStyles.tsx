import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDocumentCommentActionsStyles = makeStyles({
    actions: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
});
