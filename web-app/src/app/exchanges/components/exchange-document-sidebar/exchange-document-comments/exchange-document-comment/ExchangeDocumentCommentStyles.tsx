import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDocumentCommentStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalS
    },
    commentTextContainer: {
        display: "flex",
        flexGrow: 1,
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS
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
        padding: tokens.spacingHorizontalXS
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
        gap: tokens.spacingHorizontalXS
    }
});
