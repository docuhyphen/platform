import {makeStyles} from "@fluentui/react-components";

export const useExchangeDocumentCommentComposerStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        width: "100%",
    },
    field: {
        alignItems: "start",
        paddingTop: "8px",
        width: "100%",
    },
    actions: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "space-between",
        gap: "8px",
        flexWrap: "wrap",
    },
});
