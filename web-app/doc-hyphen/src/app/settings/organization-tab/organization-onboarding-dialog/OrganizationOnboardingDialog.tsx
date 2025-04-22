import {Dialog, DialogBody, DialogContent, DialogSurface, DialogTitle, Text} from "@fluentui/react-components";
import React from "react";
import OrganizationOnboardingForm from "../../../onboarding/organization-onboarding/OrganizationOnboardingForm.tsx";

interface OrganizationOnboardingDialogProps
{
    isOpen: boolean,
    onDismiss: () => void,
}

const OrganizationOnboardingDialog: React.FC<OrganizationOnboardingDialogProps> = (
    {
        isOpen,
        onDismiss
    }) =>
{
    const [orgRegistered, setOrgRegistered] = React.useState(false);

    const onOrganizationRegistered = () =>
    {
        setOrgRegistered(true);
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
                                You can now start using Doc-Hyphen to its full potential for a limited period of time
                                until we complete your verification.
                            </Text>

                        }
                        {!orgRegistered &&

                            <OrganizationOnboardingForm isOnDialog={true}
                                                        onCancel={onDismiss}
                                                        onOrganizationRegistered={onOrganizationRegistered}/>
                        }
                    </DialogContent>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    </>
}

export default OrganizationOnboardingDialog;