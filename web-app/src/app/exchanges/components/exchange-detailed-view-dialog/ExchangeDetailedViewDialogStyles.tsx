import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDDetailedViewDialogStyles = makeStyles({
    dialogSurface: {
        width: "min(760px, 92vw)",
        maxHeight: "85vh",
    },

    dialogContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        maxHeight: "68vh",
        overflowY: "auto",
        paddingRight: tokens.spacingHorizontalXS,
    },
});
