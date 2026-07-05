import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowInstanceDiagramStyles = makeStyles({
    container: {
        width: "100%",
        minHeight: "420px",
        height: "100%",
    },
    placeholder: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "100%",
        minHeight: "420px",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
    },
});
