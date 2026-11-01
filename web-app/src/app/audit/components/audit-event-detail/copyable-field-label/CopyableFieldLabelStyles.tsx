import {makeStyles, tokens} from "@fluentui/react-components";

export const useCopyableFieldLabelStyles = makeStyles({
    row: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXXS,
    },
    copyButton: {
        minWidth: "unset",
        width: "20px",
        height: "20px",
        padding: 0,
    },
    copyBuffer: {
        position: "fixed",
        insetInlineStart: "-100%",
        insetBlockStart: "0",
        opacity: 0,
    },
});
