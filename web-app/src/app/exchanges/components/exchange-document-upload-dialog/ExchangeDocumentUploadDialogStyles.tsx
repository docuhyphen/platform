import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDocumentUploadDialogStyles = makeStyles({
    contentContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        minWidth: "420px",
        boxSizing: "border-box",
        "@media (max-width: 768px)": {
            minWidth: 0,
            width: "100%",
        },
    },
});
