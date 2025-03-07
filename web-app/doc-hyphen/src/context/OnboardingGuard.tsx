import { Navigate } from "react-router-dom";
import {useAuth} from "./AuthContext.tsx";

const OnboardingProtectedRoute: React.FC<{ element: JSX.Element }> = ({ element }) => {
    const { token, appUser } = useAuth();

    if (!token) {
        return <Navigate to="/sign-in" replace />;
    }

    if (!appUser || !appUser.person) {
        return <Navigate to="/onboarding/individual-registration" replace />;
    }

    return element;
};

export default OnboardingProtectedRoute;
