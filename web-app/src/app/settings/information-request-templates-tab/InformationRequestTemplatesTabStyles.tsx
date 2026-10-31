import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestTemplatesTabStyles = makeStyles({
    root: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        height: "100%",
        minHeight: 0,
        width: "100%",
    },
});
