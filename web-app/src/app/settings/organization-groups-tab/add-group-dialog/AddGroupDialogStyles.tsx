import {makeStyles} from "@fluentui/react-components";

export const useAddGroupDialogStyles = makeStyles({
    dialogContentContainer: {
        margin: "8px 0",
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        // Allow the members table to scroll horizontally inside the
        // dialog on narrow screens (paired with the global mobile
        // DialogSurface size cap in index.css).
        overflowX: "auto",
    }
});