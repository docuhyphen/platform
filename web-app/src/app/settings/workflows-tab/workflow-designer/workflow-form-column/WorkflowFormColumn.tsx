import {ReactNode} from "react";
import {mergeClasses} from "@fluentui/react-components";
import {FormPage, FormPageTransitionDirection} from "../workflowDesignerDefaults.ts";
import {useWorkflowFormColumnStyles} from "./WorkflowFormColumnStyles.tsx";

interface Props
{
    formPage: FormPage;
    transitionDirection: FormPageTransitionDirection;
    children: ReactNode;
}

const WorkflowFormColumn = ({formPage, transitionDirection, children}: Props) =>
{
    const styles = useWorkflowFormColumnStyles();

    return (
        <div id={"workflow-designer-split-form-column"}
             className={styles.splitFormColumn}>
            <div
                id={`workflow-designer-form-page-${formPage}`}
                key={formPage}
                className={mergeClasses(
                    styles.formPageTransitionFrame,
                    transitionDirection === "forward" ? styles.formPageSlideLeft : undefined,
                    transitionDirection === "back" ? styles.formPageSlideRight : undefined,
                )}
            >
                {children}
            </div>
        </div>
    );
};

export default WorkflowFormColumn;
