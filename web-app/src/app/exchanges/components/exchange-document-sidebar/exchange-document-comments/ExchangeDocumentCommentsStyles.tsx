import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useExchangeDocumentCommentsStyles = makeStyles({

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
        color: tokens.colorNeutralForeground3,
        ...shorthands.padding("16px")
    }
});
