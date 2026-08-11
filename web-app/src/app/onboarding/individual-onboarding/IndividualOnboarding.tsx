import React, {useEffect, useState} from 'react';
import IndividualOnboardingForm from './IndividualOnboardingForm.tsx';
import {useOnboardingStyles} from '../OnboardingStyles.tsx';
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import {Spinner, Text} from "@fluentui/react-components";
import OnBoardingBreadcrumbs from "../onboarding-breadcrumbs/OnBoardingBreadcrumbs.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {useNavigate} from "react-router-dom";
import OnboardingMfaSetupStep from "./mfa-setup-step/OnboardingMfaSetupStep.tsx";

type IndividualOnboardingStep = "profile" | "mfa";

const IndividualOnboarding: React.FC = () =>
{
    const {appUser} = useAuth()
    const navigate = useNavigate();
    const styles = useOnboardingStyles();
    const [registerOrganization, setRegisterOrganization] = useState(false);
    const [step, setStep] = useState<IndividualOnboardingStep>("profile");
    const [checkingIfIndividualOnboarded, setCheckingIfIndividualOnboarded] = useState(false);

    useEffect(() =>
    {
        if (!appUser?.person)
        {
            setCheckingIfIndividualOnboarded(false)
        }
        else if (step === "profile")
        {
            setStep("mfa");
        }
    }, [appUser, step]);

    const onRegisterOrganizationChange = (newValue: boolean) =>
    {
        setRegisterOrganization(newValue);
    }

    const continueAfterMfa = () =>
    {
        navigate(registerOrganization ? '/onboarding/organization' : '/exchanges');
    };

    return <>
        {checkingIfIndividualOnboarded &&
            <Spinner id={"individual-onboarding-loading"}/>}
        {!checkingIfIndividualOnboarded &&
            <div
                id={"individual-onboarding"}
                className={styles.container}>
                <div
                    id={"individual-onboarding-section"}
                    className={styles.onboardingSection}>
                    <div
                        id={"individual-onboarding-form-section"}
                        className={styles.onboardingSection1}>
                        <div id={"individual-onboarding-logo"}>
                            <AppLogo/>
                        </div>
                        <div id={"individual-onboarding-current-step"}>
                            {step === "profile" &&
                                <IndividualOnboardingForm
                                    onRegisterOrganizationChange={onRegisterOrganizationChange}
                                    onProfileRegistered={() => setStep("mfa")}
                                />}
                            {step === "mfa" &&
                                <OnboardingMfaSetupStep
                                    onComplete={continueAfterMfa}
                                    onSkip={continueAfterMfa}
                                />}
                        </div>
                        <div id={"individual-onboarding-form-spacer"}></div>
                    </div>
                    <div
                        id={"individual-onboarding-progress-section"}
                        className={styles.onboardingSection2}>
                        <div
                            id={"individual-onboarding-progress-content"}
                            className={styles.onboardingSection2_1}>
                            <div id={"individual-onboarding-welcome"}>
                                <Text
                                    id={"individual-onboarding-welcome-title"}
                                    size={500}>
                                    Welcome
                                </Text>
                                <Text
                                    id={"individual-onboarding-welcome-copy"}
                                    size={400}>
                                    to <Text italic={true}>DocuHyphen</Text>! We're thrilled to have you on board. Let's
                                    get you set up.
                                </Text>
                        </div>
                        <p id={"individual-onboarding-progress-copy"}>
                            Here’s your progress before you can start exchanging documents.
                        </p>
                        <OnBoardingBreadcrumbs
                            registerOrganization={registerOrganization}
                            isIndividualOnboarding={step === "profile"}
                            isMfaOnboarding={step === "mfa"}
                            isOnboardingComplete={false}
                            isOrgOnboarding={false}
                        />
                        </div>

                    </div>
                </div>
            </div>
        }
    </>
};

export default IndividualOnboarding;
