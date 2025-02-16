import React from 'react';
import IndividualRegistration from '../individual-registration/IndividualRegistration';
import {useOnboardingStyles} from './OnboardingStyles';

const Onboarding: React.FC = () => {
    const styles = useOnboardingStyles();

    return (
        <div className={styles.container}>
            <h1 className={styles.heading}>Welcome,</h1>
            <p className={styles.paragraph}>Please complete your registration</p>
            <IndividualRegistration />
        </div>
    );
};

export default Onboarding;