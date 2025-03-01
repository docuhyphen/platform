import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useSessionDocumentsListStyles = makeStyles({

    container: {
        width: "100%",
        overflow: "hidden",
        display: "flex",
        flexDirection: "column",
        gap: "8px"
    },

    searchSection: {
        display: "flex",
        gap: "6px",
        alignItems: "center",
    },

    cardListSection: {
        display: "flex",
        overflowX: "hidden",
        whiteSpace: "nowrap",
        ...shorthands.padding("2px"),
        gap: "16px",
        scrollbarWidth: "none", // Firefox

        "&::-webkit-scrollbar": {
            display: "none", // Chrome, Safari, Edge
        },

        "&:hover": {
            overflowX: "auto",
            scrollbarWidth: "thin", // Firefox
        },

        "&:hover::-webkit-scrollbar": {
            display: "block",
            height: "8px",
        },

        "&:hover::-webkit-scrollbar-thumb": {
            backgroundColor: tokens.colorNeutralStroke1Hover,
            borderRadius: "4px",
        },

        "&:hover::-webkit-scrollbar-track": {
            backgroundColor: tokens.colorNeutralBackground1,
        },
    },

    documentsCard: {
        minWidth: "300px",
        maxWidth: "300px",
        flex: "0 0 auto"
    },

    documentsCardSelected: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorBrandForeground1
    },

    searchField: {
        flex: "1"
    },
});