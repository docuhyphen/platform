import {makeStyles, tokens} from "@fluentui/react-components";

export const useOneOffRequestFieldsStyles = makeStyles({
    fields: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        minWidth: 0,
    },
});
