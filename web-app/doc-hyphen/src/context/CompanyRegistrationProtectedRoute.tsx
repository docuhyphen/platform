import {Navigate} from "react-router-dom";
import {useAuth} from "./AuthContext.tsx";

export const CompanyRegistrationProtectedRoute: React.FC<{ element: JSX.Element }> = ({element}) =>
{
    const {token, appUser, appUserPersonCompany} = useAuth();

    if (!token)
    {
        return <Navigate to="/sign-in" replace/>;
    }

    if (!appUser || !appUser.person)
    {
        return <Navigate to="/onboarding/individual" replace/>;
    }

    if (appUserPersonCompany)
    {
        return <Navigate to="/sharing-sessions" replace/>;
    }

    return element;
};
