import {ChangeEvent, useState} from 'react';
import {CheckboxOnChangeData, InputOnChangeData} from "@fluentui/react-components";
import validator from 'validator';
import {fetchAppUser, registerOrganization} from "../../../services/appUserApi.ts";
import useToken from "../../../context/useToken.tsx";
import {OrganizationBasicDto, OrganizationDetailedDto, ResponseError} from "../../models/models.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";

type InputChangeHandler = (event: ChangeEvent<HTMLInputElement>, data: InputOnChangeData) => void;

export interface OrganizationRegistrationState
{
    organizationName: string;
    organizationEmail: string;
    registrationNumber: string;
    organizationPhone: string;
    registeringOrg: boolean;
    orgRegistered: boolean;
    errorMessage?: string;
    appUserPersonOrganization: OrganizationDetailedDto | null;
    onOrganizationNameChange: InputChangeHandler;
    onRegistrationNumberChange: InputChangeHandler;
    onOrganizationEmailChange: InputChangeHandler;
    onOrganizationPhoneChange: InputChangeHandler;
    onUseAppUserEmailCheck: (event: ChangeEvent<HTMLInputElement>, data: CheckboxOnChangeData) => void;
    onRegisterOrganization: () => Promise<void>;
    onDismissError: () => void;
}

export const useOrganizationRegistration = (
    onOrganizationRegistered?: (organization: OrganizationBasicDto) => void
): OrganizationRegistrationState =>
{
    const [organizationName, setOrganizationName] = useState('');
    const [organizationEmail, setOrganizationEmail] = useState('');
    const [registrationNumber, setRegistrationNumber] = useState('');
    const [organizationPhone, setOrganizationPhone] = useState('');
    const [registeringOrg, setRegisteringOrg] = useState(false);
    const [orgRegistered, setOrgRegistered] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | undefined>();
    const token = useToken();
    const {setAppUserPersonOrganization, appUserPersonOrganization, appUser, setAppUser, token: authToken} = useAuth();

    const validateInputs = (): boolean =>
    {
        if (!organizationName.trim())
        {
            setErrorMessage("Organization name is required.");
            return false;
        }
        if (!registrationNumber.trim())
        {
            setErrorMessage("Registration number is required.");
            return false;
        }
        if (organizationEmail.trim() && !validator.isEmail(organizationEmail.trim()))
        {
            setErrorMessage("Please enter a valid organization email.");
            return false;
        }
        if (organizationPhone.trim() && !validator.isMobilePhone(organizationPhone.trim(), 'any', {strictMode: false}))
        {
            setErrorMessage("Please enter a valid phone number.");
            return false;
        }
        return true;
    };

    const onRegisterOrganization = async () =>
    {
        if (registeringOrg) return;

        setErrorMessage(undefined);

        if (!validateInputs())
        {
            return;
        }

        setRegisteringOrg(true);

        try
        {
            const organization = {
                name: organizationName.trim(),
                registrationNumber: registrationNumber.trim(),
                email: organizationEmail.trim(),
                phoneNumber: organizationPhone.trim(),
            };
            const registeredOrganization: OrganizationBasicDto = await registerOrganization(organization, token);

            setAppUserPersonOrganization(registeredOrganization);
            setOrgRegistered(true);

            try
            {
                const updatedUser = await fetchAppUser(authToken);
                setAppUser(updatedUser);
            }
            catch (e)
            {
                console.warn("Failed to refresh user after org registration", e);
            }

            if (onOrganizationRegistered)
            {
                onOrganizationRegistered(registeredOrganization);
            }
        }
        catch (error)
        {
            console.error('Registration failed', error);
            setErrorMessage((error as ResponseError)?.errorMessage || "Failed to register organization. Please try again.");
        }
        finally
        {
            setRegisteringOrg(false);
        }
    };

    const onUseAppUserEmailCheck = (_ev: ChangeEvent<HTMLInputElement>, data: CheckboxOnChangeData) =>
    {
        if (data.checked)
        {
            setOrganizationEmail(appUser.email);
        }
        else
        {
            setOrganizationEmail('');
        }
    };

    return {
        organizationName,
        organizationEmail,
        registrationNumber,
        organizationPhone,
        registeringOrg,
        orgRegistered,
        errorMessage,
        appUserPersonOrganization,
        onOrganizationNameChange: (_e, newValue) => setOrganizationName(newValue.value || ''),
        onRegistrationNumberChange: (_e, newValue) => setRegistrationNumber(newValue.value || ''),
        onOrganizationEmailChange: (_e, newValue) => setOrganizationEmail(newValue.value || ''),
        onOrganizationPhoneChange: (_e, newValue) => setOrganizationPhone(newValue.value || ''),
        onUseAppUserEmailCheck,
        onRegisterOrganization,
        onDismissError: () => setErrorMessage(undefined),
    };
};
