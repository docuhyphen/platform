import {useEffect, useState} from "react";
import {listExchangeAccess} from "../../../../../services/exchangeApi.ts";
import {ExchangeAccessEntryDto} from "../../../../../services/types/dtos.ts";

export const useParticipantSummary = (exchangeId: string) =>
{
    const [participants, setParticipants] = useState<ExchangeAccessEntryDto[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() =>
    {
        let cancelled = false;
        setLoading(true);
        void listExchangeAccess(exchangeId)
            .then(entries =>
            {
                if (cancelled) return;
                setParticipants(entries.filter(entry =>
                    entry.recipientPurpose === "PARTICIPANT" &&
                    entry.source === "DIRECT" &&
                    entry.status !== "REVOKED" &&
                    entry.status !== "EXPIRED"));
            })
            .catch(() =>
            {
                if (!cancelled) setParticipants([]);
            })
            .finally(() =>
            {
                if (!cancelled) setLoading(false);
            });

        return () =>
        {
            cancelled = true;
        };
    }, [exchangeId]);

    return {participants, loading};
};
