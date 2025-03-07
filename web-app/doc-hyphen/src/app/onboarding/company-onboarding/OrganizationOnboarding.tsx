import React, {useState} from 'react';
import IndividualOnboardingForm from './IndividualOnboardingForm.tsx';
import {useOnboardingStyles} from '../OnboardingStyles.tsx';
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import {Text} from "@fluentui/react-components";
import OnboardingBreadcrumbs from "../onboarding-breadcrumbs/OnBoardingBreadcrumbs.tsx";
import OrganizationOnboardingForm from "./OrganizationOnboardingForm.tsx";


const OrganizationOnboarding: React.FC = () =>
{
    const styles = useOnboardingStyles();

    return (
        <div className={styles.container}>
            <div className={styles.onboardingSection}>
                <div className={styles.onboardingSection1}>
                    <div>
                        <AppLogo/>
                    </div>
                    <div>
                        <OrganizationOnboardingForm/>
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
        </div>
    );
};

export default OrganizationOnboarding;