import {makeStyles} from "@fluentui/react-components";

export const useProfileTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "24px",
        width: "100%",
        minWidth: 0,
    },

    dataContainer: {
        display: "flex",
        flexDirection: "row",
        flexWrap: "wrap",
        gap: "16px",
        wordBreak: "break-word",
    },

    dataEditable: {
        display: "flex",
        gap: "8px",
        alignItems: "center",
        flexWrap: "wrap",
    },

    mainDivider: {
        width: "300px",
        maxWidth: "100%",
        "@media (max-width: 768px)": {
            width: "100%",
        },
    }
});