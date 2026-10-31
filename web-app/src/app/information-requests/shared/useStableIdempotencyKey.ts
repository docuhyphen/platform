import {createContext, useContext, useMemo, useState} from "react";

export type StableKeyedCommand = <T>(
    identity: string,
    body: unknown,
    command: (idempotencyKey: string) => Promise<T>,
) => Promise<T>;

export const CommandKeysContext = createContext<Map<string, string> | null>(null);

export const commandKeysFor = (store: Map<string, Map<string, string>>, owner: string): Map<string, string> => {
    const kept = store.get(owner);
    if (kept) return kept;
    const created = new Map<string, string>();
    store.set(owner, created);
    return created;
};

export const stableKeyedCommand = (
    keys: Map<string, string>,
    newIdempotencyKey: () => string = () => crypto.randomUUID(),
): StableKeyedCommand =>
    async <T>(identity: string, body: unknown, command: (idempotencyKey: string) => Promise<T>): Promise<T> => {
        const signature = `${identity}|${JSON.stringify(body)}`;
        const key = keys.get(signature) ?? newIdempotencyKey();
        keys.set(signature, key);
        const answered = await command(key);
        keys.delete(signature);
        return answered;
    };

export const useCommandKeys = (): Map<string, string> => {
    const shared = useContext(CommandKeysContext);
    const [own] = useState(() => new Map<string, string>());
    return shared ?? own;
};

export const useStableIdempotencyKey = (newIdempotencyKey?: () => string): StableKeyedCommand => {
    const keys = useCommandKeys();
    return useMemo(() => stableKeyedCommand(keys, newIdempotencyKey), [keys, newIdempotencyKey]);
};
