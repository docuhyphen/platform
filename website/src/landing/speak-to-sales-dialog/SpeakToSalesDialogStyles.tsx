import {makeStyles} from "@fluentui/react-components";
import {SPACE_SM} from "../shared.ts";

export const useSpeakToSalesDialogStyles = makeStyles({
    surface: {
        maxWidth: "40rem",
        width: "100%",
    },

    actions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: SPACE_SM,
    },
});
