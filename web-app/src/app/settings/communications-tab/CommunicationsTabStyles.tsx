import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationsTabStyles = makeStyles({
    outerContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        width: "100%",
        height: "100%",
        minHeight: 0,
    },
});
