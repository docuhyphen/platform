import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDocumentCommentOptionsStyles = makeStyles({
    internalOptions: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        width: "100%",
    },
});
