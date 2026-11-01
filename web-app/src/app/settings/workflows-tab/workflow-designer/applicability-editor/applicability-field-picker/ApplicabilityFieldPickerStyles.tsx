import {makeStyles, tokens} from "@fluentui/react-components";

export const useApplicabilityFieldPickerStyles = makeStyles({
    schemaPicker: {
        display: "flex",
        flexDirection: "column",
        rowGap: tokens.spacingVerticalXS,
        maxWidth: "360px",
        width: "100%",
    },
});
