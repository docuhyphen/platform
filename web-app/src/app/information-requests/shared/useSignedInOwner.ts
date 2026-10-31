import {useAuth} from "../../../context/AuthContext.tsx";

export const useSignedInOwner = (): string | null => {
    const {currentSession} = useAuth();
    return currentSession ? `${currentSession.userId}|${currentSession.activeOrganizationId ?? ""}` : null;
};
