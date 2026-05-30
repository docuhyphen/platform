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
        overflowX: "auto",
        whiteSpace: "nowrap",
        ...shorthands.padding("2px"),
        gap: "16px",
        scrollbarWidth: "none",

        "&:hover": {
            scrollbarWidth: "thin",
            scrollbarColor: `${tokens.colorNeutralForeground3} transparent`,
        },

        "&::-webkit-scrollbar": {
            display: "none",
            height: "7px",
        },

        "&:hover::-webkit-scrollbar": {
            display: "block",
        },

        "&::-webkit-scrollbar-thumb": {
            backgroundColor: "transparent",
            borderRadius: "4px",
            transition: "background-color 180ms ease",
        },

        "&:hover::-webkit-scrollbar-thumb": {
            backgroundColor: tokens.colorNeutralForeground3,
        },

        "&::-webkit-scrollbar-thumb:hover": {
            backgroundColor: tokens.colorNeutralForeground2,
        },

        "&::-webkit-scrollbar-track": {
            backgroundColor: "transparent",
        },
    },

    documentsCard: {
        minWidth: "300px",
        maxWidth: "300px",
        flex: "0 0 auto",
    },

    documentsCardSelected: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorBrandForeground1
    },

    searchField: {
        flex: "1"
    },
});