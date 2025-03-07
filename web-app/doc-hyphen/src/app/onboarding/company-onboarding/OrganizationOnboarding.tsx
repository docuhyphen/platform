import React, {useState} from 'react';
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


const OrganizationOnboarding: React.FC = () =>
{
    const globalStyles = useGlobalStyles();
    const styles = useOnboardingStyles();
    const navigate = useNavigate();
    const [isSkipOrgOnboardingDialogOpen, setIsSkipOrgOnboardingDialogOpen] = useState(false);

    return (
        <div className={styles.container}>
            <div className={styles.onboardingSection}>
                <div className={styles.onboardingSection1}>
                    <div>
                        <AppLogo/>
                    </div>
                    <div>
                        <OrganizationOnboardingForm/>

                        <Button appearance={"transparent"}
                                onClick={() => setIsSkipOrgOnboardingDialogOpen(true)}>
                            Skip for later
                        </Button>
                    </div>
                    <div></div>
                </div>
                <div className={styles.onboardingSection2}>
                    <div className={styles.onboardingSection2_1}>
                        <Text size={400}>
                            Welcome to <Text italic={true}>Doc-Hyphen</Text>! We're thrilled to have you on board. Let's
                            get you set you up.
                        </Text>
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
                        <DialogTitle>Skipping organization registration</DialogTitle>
                        <DialogContent>
                            <p>
                                Are you sure you want to skip organization registration?
                            </p>
                            <Text>
                                By skipping organization registration, your features will be limited.
                                But don't worry, you can always register your organization later.
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