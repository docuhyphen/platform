import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestStructuredResponseWorkspaceStyles = makeStyles({
    root: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        minHeight: 0,
        height: "100%",
        boxSizing: "border-box",
    },
    nothing: {
        color: tokens.colorNeutralForeground3,
    },
});
