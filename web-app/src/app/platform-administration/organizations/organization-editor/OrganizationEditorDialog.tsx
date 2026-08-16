import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
} from "@fluentui/react-components";
import {PlatformOrganizationSummary} from "../../../../services/types/platformOrganizations.ts";
import OrganizationEditorForm, {OrganizationEditorSaveContent} from "./OrganizationEditorForm.tsx";
import {useOrganizationEditorStyles} from "./OrganizationEditorStyles.tsx";
import {useOrganizationEditor} from "./useOrganizationEditor.ts";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {useState} from "react";
import {
    extendPlatformOrganizationSubscriptionTrial,
    endPlatformOrganizationSubscriptionTrial,
    convertPlatformOrganizationSubscriptionTrial,
    startPlatformOrganizationSubscriptionTrial,
} from "../../../../services/platformOrganizationApi.ts";
import SubscriptionTrialDialog from "../../subscription-trial-dialog/SubscriptionTrialDialog.tsx";
import SubscriptionTrialTransitionDialog from "../../subscription-trial-transition-dialog/SubscriptionTrialTransitionDialog.tsx";
import {SubscriptionTrialTransitionMode} from "../../subscription-trial-transition-dialog/useSubscriptionTrialTransitionDialog.ts";

interface OrganizationEditorDialogProps
{
    organization: PlatformOrganizationSummary | null;
    onDismiss: () => void;
    onSaved: () => void;
}

const OrganizationEditorDialog = ({organization, onDismiss, onSaved}: OrganizationEditorDialogProps) =>
{
    const styles = useOrganizationEditorStyles();
    const {refreshCurrentSession} = useAuth();
    const editor = useOrganizationEditor(organization, onSaved, refreshCurrentSession);
    const [trialOpen, setTrialOpen] = useState(false);
    const [trialTransitionMode, setTrialTransitionMode] = useState<SubscriptionTrialTransitionMode | null>(null);
    const activeTrial = organization?.subscriptionStatus === "TRIALING"
        && organization.currentPeriodEnd !== null
        && new Date(organization.currentPeriodEnd).getTime() > Date.now();

    return (
        <>
            <Dialog
                open={organization !== null && !trialOpen && trialTransitionMode === null}
                onOpenChange={(_, data) => !data.open && !editor.saving && onDismiss()}>
                <DialogSurface id={"platform-organization-editor-surface"}>
                <DialogBody
                    id={"platform-organization-editor-body"}
                    className={styles.dialogBody}>
                    <DialogTitle id={"platform-organization-editor-title"}>
                        Edit {organization?.name ?? "organization"}
                    </DialogTitle>
                    <DialogContent
                        id={"platform-organization-editor-content"}
                        className={styles.content}>
                        <OrganizationEditorForm
                            editor={editor}
                            onManageTrial={() => setTrialOpen(true)}
                            onEndTrial={() => setTrialTransitionMode("END")}
                            onConvertTrial={() => setTrialTransitionMode("CONVERT")}/>
                    </DialogContent>
                    <DialogActions
                        id={"platform-organization-editor-actions"}
                        className={styles.footer}>
                        <Button
                            id={"platform-organization-editor-save"}
                            appearance={"primary"}
                            shape={"circular"}
                            disabled={editor.saving}
                            onClick={() => void editor.save()}>
                            <OrganizationEditorSaveContent saving={editor.saving}/>
                        </Button>
                        <Button
                            id={"platform-organization-editor-cancel"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={editor.saving}
                            onClick={onDismiss}>
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
                </DialogSurface>
            </Dialog>
            <SubscriptionTrialDialog
                open={trialOpen && organization !== null}
                ownerName={organization?.name ?? "organization"}
                ownerKind={"organization"}
                isExtension={activeTrial}
                currentPeriodEnd={organization?.currentPeriodEnd ?? null}
                onDismiss={() => setTrialOpen(false)}
                onSaved={onSaved}
                onStart={(durationDays, seatCapacity, reason) => organization
                    ? startPlatformOrganizationSubscriptionTrial(organization.organizationId, {
                        planCode: "BUSINESS",
                        durationDays,
                        seatCapacity: seatCapacity ?? 5,
                        reason,
                    })
                    : Promise.resolve()}
                onExtend={(currentPeriodEnd, reason) => organization
                    ? extendPlatformOrganizationSubscriptionTrial(organization.organizationId, {
                        currentPeriodEnd,
                        reason,
                    })
                    : Promise.resolve()}/>
            <SubscriptionTrialTransitionDialog
                open={trialTransitionMode !== null && organization !== null}
                ownerName={organization?.name ?? "organization"}
                ownerKind={"organization"}
                mode={trialTransitionMode ?? "END"}
                defaultSeatCapacity={organization?.maxUsers ?? 5}
                onDismiss={() => setTrialTransitionMode(null)}
                onSaved={onSaved}
                onEnd={(reason) => organization
                    ? endPlatformOrganizationSubscriptionTrial(organization.organizationId, {reason})
                    : Promise.resolve()}
                onConvert={(billingFrequency, currentPeriodEnd, seatCapacity, reason) => organization
                    ? convertPlatformOrganizationSubscriptionTrial(organization.organizationId, {
                        billingFrequency,
                        currentPeriodEnd,
                        seatCapacity: seatCapacity ?? 5,
                        reason,
                    })
                    : Promise.resolve()}/>
        </>
    );
};

export default OrganizationEditorDialog;
