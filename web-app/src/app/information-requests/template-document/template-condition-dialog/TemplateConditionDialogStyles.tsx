import {makeStyles, tokens} from "@fluentui/react-components";

export const useTemplateConditionDialogStyles = makeStyles({
    predicates: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    addButton: {
        alignSelf: "flex-start",
    },
});
