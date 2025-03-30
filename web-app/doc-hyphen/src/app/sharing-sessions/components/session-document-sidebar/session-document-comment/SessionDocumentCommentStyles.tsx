import {makeStyles, shorthands} from "@fluentui/react-components";

export const useSessionDocumentCommentStyles = makeStyles({
    commentContainer: {
        ...shorthands.margin("8px", 0),
        ...shorthands.padding("8px"),
        backgroundColor: "#f5f5f5",
        ...shorthands.borderRadius("4px")
    },
    commentHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        ...shorthands.margin(0, 0, "4px", 0)
    },
    commentText: {
        wordBreak: "break-word",
        whiteSpace: "pre-wrap"
    }
});