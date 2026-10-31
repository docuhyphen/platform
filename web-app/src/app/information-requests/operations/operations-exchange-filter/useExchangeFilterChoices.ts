import {useEffect, useState} from "react";
import {searchExchanges} from "../../../../services/exchangeApi.ts";

export interface ExchangeFilterChoice {
    id: string;
    name: string;
}

const SEARCH_DELAY_MS = 300;
const CHOICE_LIMIT = 10;

export const useExchangeFilterChoices = (query: string, active: boolean): ExchangeFilterChoice[] => {
    const [choices, setChoices] = useState<ExchangeFilterChoice[]>([]);

    useEffect(() => {
        if (!active) return;
        let current = true;
        const timer = setTimeout(() => {
            searchExchanges(query.trim() || undefined, undefined, undefined, 0, CHOICE_LIMIT)
                .then(result => {
                    if (!current || !("content" in result)) return;
                    setChoices(result.content.map(exchange => ({
                        id: exchange.id,
                        name: exchange.name || "Unnamed Exchange"
                    })));
                })
                .catch(() => {
                    if (current) setChoices([]);
                });
        }, SEARCH_DELAY_MS);
        return () => {
            current = false;
            clearTimeout(timer);
        };
    }, [query, active]);

    return choices;
};
