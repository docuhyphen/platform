import {makeStyles, tokens} from "@fluentui/react-components";

export const useApplicabilitySchemaSelectStyles = makeStyles({
    schemaPicker: {
        display: "flex",
        flexDirection: "column",
        rowGap: tokens.spacingVerticalXS,
        maxWidth: "360px",
        width: "100%",
    },
    hint: {
        color: tokens.colorNeutralForeground3,
    },
});
