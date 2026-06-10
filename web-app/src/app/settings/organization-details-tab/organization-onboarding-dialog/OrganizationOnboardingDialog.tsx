import {Dialog, DialogBody, DialogContent, DialogSurface, DialogTitle, Text} from "@fluentui/react-components";
import React from "react";
import OrganizationOnboardingForm from "../../../onboarding/organization-onboarding/OrganizationOnboardingForm.tsx";
import {OrganizationBasicDto} from "../../../models/models.tsx";

interface OrganizationOnboardingDialogProps
{
    isOpen: boolean,
    onDismiss: () => void,
    onRegistered?: (organization: OrganizationBasicDto) => void,
}

const OrganizationOnboardingDialog: React.FC<OrganizationOnboardingDialogProps> = (
    {
        isOpen,
        onDismiss,
        onRegistered
    }) =>
{
    const [orgRegistered, setOrgRegistered] = React.useState(false);

    const onOrganizationRegistered = (organization: OrganizationBasicDto) =>
    {
        setOrgRegistered(true);
        onRegistered?.(organization);
    }

    const handleDismiss = () =>
    {
        setOrgRegistered(false);
        onDismiss();
    }

    return <>
        <Dialog open={isOpen} modalType={"alert"}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Registering your organization</DialogTitle>
                    <DialogContent>

                        {orgRegistered &&
                            <Text>
                                Congratulations! Your organization has been registered successfully.
                                You can now start using DocHyphen to its full potential for a limited period of time
                                until we complete your verification.
                            </Text>

                        }
                        <OrganizationOnboardingForm isOnDialog={true}
                                                        onCancel={handleDismiss}
                                                        onOrganizationRegistered={onOrganizationRegistered}/>
                    </DialogContent>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    </>
}

export default OrganizationOnboardingDialog;