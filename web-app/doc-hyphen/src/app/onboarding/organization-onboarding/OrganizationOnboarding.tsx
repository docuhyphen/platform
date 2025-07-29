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
                navigate('/sharing-sessions');
            }
            catch (error: any)
            {
                if (error.response?.status === 404)
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
        <div className={styles.container}>
            <div className={styles.onboardingSection}>
                <div className={styles.onboardingSection1}>
                    <AppLogo/>
                    <div className={styles.orgOnboardingContainer}>
                        <OrganizationOnboardingForm onOrganizationRegistered={
                            (organization) =>
                            {
                                navigate('/sharing-sessions');
                            }
                        }/>
                    </div>
                    <Button appearance={"transparent"}
                            onClick={() => setIsSkipOrgOnboardingDialogOpen(true)}>
                        Skip for later
                    </Button>
                </div>
                <div className={styles.onboardingSection2}>
                    <div className={styles.onboardingSection2_1}>
                        <div>
                            <Text size={500}>Welcome </Text>
                            <Text size={400}>
                                to <Text italic={true}>Doc-Hyphen</Text>! We're thrilled to have you on board. Let's
                                get you set up.
                            </Text>
                        </div>
                        <p>
                            Here’s your progress before you can start sharing documents.
                        </p>
                        <OnboardingBreadcrumbs
                            registerOrganization={true}
                            isIndividualOnboarding={false}
                            isOnboardingComplete={false}
                            isOrgOnboarding={true}
                        />
                    </div>
                </div>
            </div>
            <Dialog modalType="alert"
                    open={isSkipOrgOnboardingDialogOpen}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Skip Organization Registration</DialogTitle>
                        <DialogContent>
                            <p>
                                Are you sure you want to skip registering your organization?
                            </p>
                            <Text>
                                Skipping registration will limit some features, but you can always complete it later.
                            </Text>
                        </DialogContent>
                        <DialogActions>
                            <Button appearance="primary"
                                    className={globalStyles.buttonWithLoading}
                                    shape={"circular"}
                                    onClick={() => navigate('/sharing-sessions')}>
                                Yes, Skip Registration
                            </Button>
                            <DialogTrigger disableButtonEnhancement>
                                <Button appearance="secondary"
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