// web-app/doc-hyphen/src/app/sharing-sessions/components/session-document-comments/SessionDocumentCommentsStyles.tsx
import {makeStyles, shorthands} from "@fluentui/react-components";

export const useSessionDocumentCommentsStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        height: "100%",
        position: "relative"
    },

    list: {
        padding: "4px 8px",
        display: "flex",
        flexDirection: "column",
        flexGrow: 1,
        gap: "8px",
        overflowY: "auto",
    },

    noComments: {
        textAlign: "center",
        color: "#666",
        ...shorthands.padding("16px")
    },

    commentFieldContainer: {
        display: "flex",
        gap: "4px",
        flexDirection: "column"
    },

    commentFieldContainerField: {
        display: "flex",
        // alignItems: "center",
        paddingTop: "8px",
        borderTop: "1px solid #e0e0e0",
        gap: "4px",
        flex: "1"
    },

    commentField: {
        alignItems: "start",
        gap: "4px",
        width: "100%",
        transition: "* 0.2s ease",
        "& textarea": {
            transition: "* 0.2s ease",
            minHeight: "36px"
        },
        "& textarea:active, & textarea:focus": {
            height: "100px"
        },
        "& textarea:not(:active):not(:focus)": {
            height: "40px"
        }
    },

    commentCounter: {
        display: "flex",
        justifyContent: "space-between",
        flexDirection: "row"
    }
});