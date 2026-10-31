import {useCallback, useState} from "react";
import {Scoped, useScopeGuard, valueInScope} from "../shared/useScopeGuard.ts";

export interface ReviewMessage {
    intent: "success" | "warning" | "error";
    text: string;
}

export const useReviewFeedback = (scopeKey: string) => {
    const guard = useScopeGuard(scopeKey);
    const [message, setMessage] = useState<Scoped<ReviewMessage> | null>(null);

    const say = useCallback((owner: string, shown: ReviewMessage) => {
        if (guard.isCurrent(owner)) setMessage({scope: owner, value: shown});
    }, [guard]);

    const clear = useCallback((owner: string) => {
        setMessage(current => current?.scope === owner ? null : current);
    }, []);

    return {message: valueInScope(message, scopeKey), say, clear};
};

export type ReviewFeedback = ReturnType<typeof useReviewFeedback>;
