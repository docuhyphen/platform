import React, {useEffect, useState} from 'react';
import {useOnboardingStyles} from '../OnboardingStyles.tsx';
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Text
} from "@fluentui/react-components";
import OnboardingBreadcrumbs from "../onboarding-breadcrumbs/OnBoardingBreadcrumbs.tsx";
import OrganizationOnboardingForm from "./OrganizationOnboardingForm.tsx";
import {useNavigate} from "react-router-dom";
import {useGlobalStyles} from "../../../GlobalStyles.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {fetchAppUserPersonOrganization} from "../../../services/appUserApi.ts";
import {responseStatusOf} from "../../../services/requestFailure.ts";


const OrganizationOnboarding: React.FC = () =>
{
    const {appUser, setAppUserPersonOrganization, token} = useAuth()
    const globalStyles = useGlobalStyles();
    const styles = useOnboardingStyles();
    const navigate = useNavigate();
    const [isSkipOrgOnboardingDialogOpen, setIsSkipOrgOnboardingDialogOpen] = useState(false);

    const fetchOrganization = async () =>
    {
        if (appUser)
        {
            try
            {
                setAppUserPersonOrganization(await fetchAppUserPersonOrganization(appUser?.id, appUser?.person?.id, token!!));
                navigate('/exchanges');
            }
            catch (error: unknown)
            {
                if (responseStatusOf(error) === 404)
                {
                    console.log("Organization not found for user");
                }
            }
        }
    }

    useEffect(() =>
    {
        fetchOrganization()
    }, [appUser]);

    return (
        <div id={"organization-onboarding-div-1"}
             className={styles.container}>
            <div id={"organization-onboarding-div-2"}
                 className={styles.onboardingSection}>
                <div id={"organization-onboarding-div-3"}
                     className={styles.onboardingSection1}>
                    <AppLogo/>
                    <div id={"organization-onboarding-div-4"}
                         className={styles.orgOnboardingContainer}>
                        <OrganizationOnboardingForm onOrganizationRegistered={
                            () =>
                            {
                                navigate('/exchanges');
                            }
                        }/>
                    </div>
                    <Button id={"org-onboarding-skip-btn"}
                            appearance={"transparent"}
                            shape={"circular"}
                            onClick={() => setIsSkipOrgOnboardingDialogOpen(true)}>
                        Skip for later
                    </Button>
                </div>
                <div id={"organization-onboarding-div-5"}
                     className={styles.onboardingSection2}>
                    <div id={"organization-onboarding-div-6"}
                         className={styles.onboardingSection2_1}>
                        <div id={"organization-onboarding-div-7"}>
                            <Text id={"organization-onboarding-text-1"}
                                  size={500}>Welcome </Text>
                            <Text id={"organization-onboarding-text-2"}
                                  size={400}>
                                to <Text id={"organization-onboarding-text-3"}
                                         italic={true}>DocHyphen</Text>! We're thrilled to have you on board. Let's
                                get you set up.
                            </Text>
                        </div>
                        <p id={"organization-onboarding-p-1"}>
                            Here’s your progress before you can start exchanging documents.
                        </p>
                        <OnboardingBreadcrumbs
                            registerOrganization={true}
                            isIndividualOnboarding={false}
                            isMfaOnboarding={false}
                            isOnboardingComplete={false}
                            isOrgOnboarding={true}
                        />
                    </div>
                </div>
            </div>
            <Dialog modalType="alert"
                    open={isSkipOrgOnboardingDialogOpen}>
                <DialogSurface id={"organization-onboarding-dialog-surface-1"}>
                    <DialogBody id={"organization-onboarding-dialog-body-1"}>
                        <DialogTitle id={"organization-onboarding-dialog-title-1"}>Skip Organization Registration</DialogTitle>
                        <DialogContent id={"organization-onboarding-dialog-content-1"}>
                            <p id={"organization-onboarding-p-2"}>
                                Are you sure you want to skip registering your organization?
                            </p>
                            <Text id={"organization-onboarding-text-4"}>
                                Skipping registration will limit some features, but you can always complete it later.
                            </Text>
                        </DialogContent>
                        <DialogActions id={"organization-onboarding-dialog-actions-1"}>
                            <Button id={"org-onboarding-skip-confirm-btn"}
                                    appearance="primary"
                                    className={globalStyles.buttonWithLoading}
                                    shape={"circular"}
                                    onClick={() => navigate('/exchanges')}>
                                Yes, Skip Registration
                            </Button>
                            <DialogTrigger disableButtonEnhancement>
                                <Button id={"org-onboarding-skip-cancel-btn"}
                                        appearance="secondary"
                                        shape={"circular"}
                                        onClick={() => setIsSkipOrgOnboardingDialogOpen(false)}>
                                    No, Register
                                </Button>
                            </DialogTrigger>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </div>
    );
};

export default OrganizationOnboarding;
