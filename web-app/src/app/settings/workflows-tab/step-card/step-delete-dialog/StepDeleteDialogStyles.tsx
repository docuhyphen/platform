import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepDeleteDialogStyles = makeStyles({
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },

    routeList: {
        marginTop: tokens.spacingVerticalXS,
        marginBottom: 0,
        paddingLeft: tokens.spacingHorizontalL,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
    },
});
