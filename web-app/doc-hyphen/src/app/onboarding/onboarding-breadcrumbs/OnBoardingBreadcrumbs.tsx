import React from 'react';
import {LocationFilled, LocationRippleFilled, LocationRippleRegular} from "@fluentui/react-icons";
import {mergeClasses, Text} from "@fluentui/react-components";
import {useOnboardingBreadcrumbsStyles} from "./OnboardingBreadcrumbsStyles.tsx";

interface BreadcrumbItemProps
{
    icon: React.ReactNode;
    text: string;
    weight?: "regular" | "semibold";
    disabled?: boolean;
    isCurrentStep?: boolean;
}

const BreadcrumbItem: React.FC<BreadcrumbItemProps> = (
    {
        icon,
        text,
        disabled = false,
        isCurrentStep = false
    }) =>
{
    const styles = useOnboardingBreadcrumbsStyles();
    return (
        <div
            className={mergeClasses(
                styles.onBoardingBreadcrumbItem,
                disabled && styles.onBoardingBreadcrumbItemDisabled)}>
            {icon && icon}
            {!icon &&
                <>

                    {isCurrentStep &&
                        <LocationRippleFilled className={styles.onBoardingBreadcrumbItemIcon}/>
                    }
                    {
                        !isCurrentStep &&
                        <LocationFilled className={styles.onBoardingBreadcrumbItemIcon}/>
                    }
                </>
            }

            <Text className={mergeClasses(
                styles.onBoardingBreadcrumbItemText,
                isCurrentStep && styles.onBoardingBreadcrumbItemCurrent)}>
                {text}
            </Text>
        </div>
    );
};

export interface OnBoardingBreadcrumbsProps
{
    registerOrganization: boolean;
    isOrgOnboarding: boolean;
    isIndividualOnboarding: boolean;
    isOnboardingComplete: boolean;
}

const OnBoardingBreadcrumbs: React.FC<OnBoardingBreadcrumbsProps> = (
    {
        registerOrganization,
        isOrgOnboarding,
        isIndividualOnboarding,
        isOnboardingComplete
    }) =>
{
    const styles = useOnboardingBreadcrumbsStyles();

    return (
        <div className={styles.onBoardingBreadcrumbs}>
            <BreadcrumbItem text="Sign Up"/>
            <BreadcrumbItem
                text="Sign In"/>
            <BreadcrumbItem
                text="Your Profile"
                isCurrentStep={isIndividualOnboarding}/>
            <BreadcrumbItem
                text="Your Organizationn"
                weight={isOrgOnboarding ? "regular" : "semibold"}
                disabled={!registerOrganization}
                isCurrentStep={isOrgOnboarding}
                icon={isIndividualOnboarding ?
                    <LocationRippleRegular className={styles.onBoardingBreadcrumbItemIcon}/> : null}
            />
            <BreadcrumbItem
                text="Start Sharing"
                isCurrentStep={isOnboardingComplete}
                icon={<LocationRippleRegular className={styles.onBoardingBreadcrumbItemIcon}/>}/>
        </div>
    );
};

export default OnBoardingBreadcrumbs;