// web-app/doc-hyphen/src/app/sharing-sessions/components/session-document-comments/SessionDocumentCommentsStyles.tsx
import {makeStyles, shorthands} from "@fluentui/react-components";

export const useSessionDocumentCommentsStyles = makeStyles({
    commentsContainer: {
        display: "flex",
        flexDirection: "column",
        height: "100%"
    },
    commentsList: {
        flexGrow: 1,
        overflowY: "auto",
        ...shorthands.padding("8px")
    },
    noComments: {
        textAlign: "center",
        color: "#666",
        ...shorthands.padding("16px")
    },
    commentFieldContainer: {
        display: "flex",
        alignItems: "flex-end",
        ...shorthands.padding("8px"),
        borderTop: "1px solid #e0e0e0"
    },
    commentField: {
        flexGrow: 1
    }
});