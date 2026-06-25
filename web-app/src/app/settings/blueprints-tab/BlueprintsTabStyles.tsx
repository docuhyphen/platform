import {makeStyles} from "@fluentui/react-components";

export const useTemplatesTabStyles = makeStyles({
    cardGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: "12px",
        "@media screen and (max-width: 768px)": {
            gridTemplateColumns: "1fr",
        },
    },
    tabContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "16px",
        width: "100%",
        minWidth: 0,
        flexWrap: "wrap",
    },
    header: {
        width: "100%",
        display: "flex",
        marginBottom: "16px",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: "8px",
    },
});