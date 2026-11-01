import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    MessageBar,
    MessageBarBody,
    Text,
} from "@fluentui/react-components";
import {AffectedRoute} from "../../workflow-designer/stepMutations.ts";
import {useStepDeleteDialogStyles} from "./StepDeleteDialogStyles.tsx";

interface Props
{
    open: boolean;
    stepNumber: number;
    stepLabel: string;
    affectedRoutes: AffectedRoute[];
    onConfirm: () => void;
    onCancel: () => void;
}

const StepDeleteDialog = ({open, stepNumber, stepLabel, affectedRoutes, onConfirm, onCancel}: Props) =>
{
    const styles = useStepDeleteDialogStyles();
    const hasAffectedRoutes = affectedRoutes.length > 0;

    return (
        <Dialog
            modalType="alert"
            open={open}
            onOpenChange={(_, d) => { if (!d.open) onCancel(); }}
        >
            <DialogSurface id={"step-delete-dialog-dialog-surface-1"}>
                <DialogBody id={"step-delete-dialog-dialog-body-1"}>
                    <DialogTitle id="step-delete-dialog-title">
                        Delete Step {stepNumber}: {stepLabel}
                    </DialogTitle>
                    <DialogContent id={"step-delete-dialog-dialog-content-1"}>
                        <div id={"step-delete-dialog-div-1"}
                             className={styles.content}>
                            <Text id="step-delete-dialog-description">
                                This permanently removes the step. Later steps shift up by one and
                                their routes are updated automatically.
                            </Text>
                            {hasAffectedRoutes && (
                                <MessageBar intent="warning"
                                            id="step-delete-dialog-affected-routes">
                                    <MessageBarBody id={"step-delete-dialog-message-bar-body-1"}>
                                        <Text id={"step-delete-dialog-text-1"}
                                              weight="semibold"
                                              block>
                                            {affectedRoutes.length === 1
                                                ? "1 route points at this step and will be rewritten to End:"
                                                : `${affectedRoutes.length} routes point at this step and will be rewritten to End:`}
                                        </Text>
                                        <ul id={"step-delete-dialog-ul-1"}
                                            className={styles.routeList}>
                                            {affectedRoutes.map((route, i) => (
                                                <li
                                                    key={i}
                                                    id={`step-delete-dialog-affected-route-${i}`}
                                                >
                                                    Step {route.stepIndex + 1}
                                                    {route.stepName ? ` (${route.stepName})` : ""}
                                                    {`, ${route.outcomeLabel}`}
                                                </li>
                                            ))}
                                        </ul>
                                    </MessageBarBody>
                                </MessageBar>
                            )}
                        </div>
                    </DialogContent>
                    <DialogActions id={"step-delete-dialog-dialog-actions-1"}>
                        <Button
                            id="step-delete-dialog-confirm-btn"
                            appearance="primary"
                            shape="circular"
                            onClick={onConfirm}
                        >
                            {hasAffectedRoutes ? "Delete and rewrite routes" : "Delete step"}
                        </Button>
                        <Button
                            id="step-delete-dialog-cancel-btn"
                            appearance="secondary"
                            shape="circular"
                            onClick={onCancel}
                        >
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default StepDeleteDialog;
