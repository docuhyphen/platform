import {Navigate} from "react-router-dom";
import {useAuth} from "./AuthContext.tsx";

export const OrganizationRegistrationProtectedRoute: React.FC<{ element: JSX.Element }> = ({element}) =>
{
    const {token, appUser, appUserPersonOrganization} = useAuth();

    if (!token)
    {
        return <Navigate to="/sign-in" replace/>;
    }

    if (!appUser || !appUser.person)
    {
        return <Navigate to="/onboarding/individual" replace/>;
    }

    if (appUserPersonOrganization)
    {
        return <Navigate to="/exchanges" replace/>;
    }

    return element;
};
