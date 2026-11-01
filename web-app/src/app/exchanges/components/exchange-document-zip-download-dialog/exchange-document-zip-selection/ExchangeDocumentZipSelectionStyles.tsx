import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDocumentZipSelectionStyles = makeStyles({
    downloadContainer: {
        margin: `${tokens.spacingVerticalL} 0`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS
    },
    downloadNameField: {}
});
