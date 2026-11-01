import {makeStyles} from "@fluentui/react-components";

export const useExchangeDetailsLoadingOverlayStyles = makeStyles({
    detailsLoadingOverlay: {
        position: "absolute",
        inset: 0,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        pointerEvents: "none",
    },
});
