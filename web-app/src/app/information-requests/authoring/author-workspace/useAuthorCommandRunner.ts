import {useRef, useState} from "react";
import {
    informationRequestRefusalMessage,
    isStaleInformationRequestCommand,
} from "../../shared/informationRequestRefusal.ts";

export const STALE_AUTHOR_NOTICE = "This request changed while you were working. The latest details are shown; try again.";

export const useAuthorCommandRunner = (reload: () => Promise<void>) =>
{
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const keys = useRef(new Map<string, string>());

    const keyFor = (signature: string): string =>
    {
        const existing = keys.current.get(signature);
        if (existing) return existing;
        const created = crypto.randomUUID();
        keys.current.set(signature, created);
        return created;
    };

    const run = async <T>(signature: string, command: (idempotencyKey: string) => Promise<T>, done?: string): Promise<T | null> =>
    {
        setBusy(true);
        setError(null);
        setNotice(null);
        try
        {
            const result = await command(keyFor(signature));
            keys.current.delete(signature);
            if (done) setNotice(done);
            await reload();
            return result;
        }
        catch (caught: unknown)
        {
            if (isStaleInformationRequestCommand(caught))
            {
                keys.current.delete(signature);
                setNotice(STALE_AUTHOR_NOTICE);
                await reload();
            }
            else
            {
                setError(informationRequestRefusalMessage(caught, "The change could not be made."));
            }
            return null;
        }
        finally
        {
            setBusy(false);
        }
    };

    return {busy, error, notice, setError, run};
};
