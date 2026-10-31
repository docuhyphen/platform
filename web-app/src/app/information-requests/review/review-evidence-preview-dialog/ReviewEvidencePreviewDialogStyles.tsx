import {makeStyles, tokens} from "@fluentui/react-components";

export const useReviewEvidencePreviewDialogStyles = makeStyles({
    surface: {width: "90vw", maxWidth: "1000px"},
    frame: {width: "100%", height: "70vh", border: 0},
    actions: {display: "flex", justifyContent: "flex-end", gap: tokens.spacingHorizontalS},
});