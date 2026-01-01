import React, {useEffect, useState} from 'react';
import IndividualOnboardingForm from './IndividualOnboardingForm.tsx';
import {useOnboardingStyles} from '../OnboardingStyles.tsx';
import AppLogo from "../../components/app-logo/AppLogo.tsx";
import {Spinner, Text} from "@fluentui/react-components";
import OnBoardingBreadcrumbs from "../onboarding-breadcrumbs/OnBoardingBreadcrumbs.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {useNavigate} from "react-router-dom";

const IndividualOnboarding: React.FC = () =>
{
    const {appUser} = useAuth()
    const navigate = useNavigate();
    const styles = useOnboardingStyles();
    const [registerOrganization, setRegisterOrganization] = useState(false);
    const [checkingIfIndividualOnboarded, setCheckingIfIndividualOnboarded] = useState(false);

    useEffect(() =>
    {
        if (!appUser?.person)
        {
            setCheckingIfIndividualOnboarded(false)
        }
        else
        {
            alert("Navigating to sharing sessions from individual onboarding");
            navigate('/sharing-sessions');
        }
    }, []);

    const onRegisterOrganizationChange = (newValue) =>
    {
        setRegisterOrganization(newValue);
    }

    return <>
        {checkingIfIndividualOnboarded && <Spinner/>}
        {!checkingIfIndividualOnboarded &&
            <div className={styles.container}>
                <div className={styles.onboardingSection}>
                    <div className={styles.onboardingSection1}>
                        <div>
                            <AppLogo/>
                        </div>
                        <div>
                            <IndividualOnboardingForm onRegisterOrganizationChange={onRegisterOrganizationChange}/>
                        </div>
                        <div></div>
                    </div>
                    <div className={styles.onboardingSection2}>
                        <div className={styles.onboardingSection2_1}>
                            <div>
                                <Text size={500}>Welcome </Text>
                                <Text size={400}>
                                    to <Text italic={true}>DocuHyphen</Text>! We're thrilled to have you on board. Let's
                                    get you set up.
                                </Text>
                            </div>
                            <p>
                                Here’s your progress before you can start sharing documents.
                            </p>
                            <OnBoardingBreadcrumbs
                                registerOrganization={registerOrganization}
                                isIndividualOnboarding={true}
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