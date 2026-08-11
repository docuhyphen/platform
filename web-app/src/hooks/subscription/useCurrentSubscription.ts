import {EffectiveSubscriptionDto} from "../../app/models/models.tsx";
import {useAuth} from "../../context/AuthContext.tsx";

export const useCurrentSubscription = (): EffectiveSubscriptionDto | null =>
{
    const {currentSession} = useAuth();
    return currentSession?.subscription ?? null;
};
