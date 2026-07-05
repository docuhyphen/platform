import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDocumentCommentStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "row",
        gap: "8px"
    },
    commentTextContainer: {
        display: "flex",
        flexGrow: 1,
        flexDirection: "column",
        gap: "4px"
    },
    commentText: {
        border: '1px dotted',
        borderTopColor: tokens.colorBrandForeground1,
        borderLeftColor: tokens.colorBrandForeground1,
        borderRightColor: tokens.colorBrandForeground1,
        borderBottomColor: tokens.colorBrandForeground1,
        whiteSpace: "pre-wrap",
        wordBreak: "break-word",
        borderRadius: tokens.borderRadiusMedium,
        padding: "4px"
    },
    commentDate: {
        display: "inline-block",
        textAlign: "end"
    },
    commentMetadata: {
        display: "flex",
        flexWrap: "wrap",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "4px"
    }
});
