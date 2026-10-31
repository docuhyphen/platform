import {useRef, useState} from "react";
import {
    informationRequestRefusalCode,
    informationRequestRefusalMessage,
    isRepeatedInformationRequestAccessLinkCommand,
    isStaleInformationRequestCommand,
} from "../../shared/informationRequestRefusal.ts";
import {Scoped, useScopeGuard, valueInScope} from "../../shared/useScopeGuard.ts";

export const STALE_AUTHOR_NOTICE = "This request changed while you were working. The latest details are shown; try again.";

const REPEATED_LINK_COMMAND_ERROR = "This link was already changed and cannot be shown again. Resend the link to create a new one.";

interface CommandStatus
{
    busy: boolean;
    error: string | null;
    notice: string | null;
}

const IDLE: CommandStatus = {busy: false, error: null, notice: null};

export const useAuthorCommandRunner = (scope: string, reload: () => Promise<void>) => {
    const [status, setStatus] = useState<Scoped<CommandStatus> | null>(null);
    const guard = useScopeGuard(scope);
    const keys = useRef(new Map<string, string>());

    const update = (owner: string, change: Partial<CommandStatus>) => {
        if (!guard.isCurrent(owner)) return;
        setStatus(current => ({scope: owner, value: {...(valueInScope(current, owner) ?? IDLE), ...change}}));
    };

    const keyFor = (signature: string): string =>
    {
        const existing = keys.current.get(signature);
        if (existing) return existing;
        const created = crypto.randomUUID();
        keys.current.set(signature, created);
        return created;
    };

    const forgetAndReload = async (owner: string, scopedSignature: string, outcome: Partial<CommandStatus>) => {
        keys.current.delete(scopedSignature);
        update(owner, outcome);
        if (guard.isCurrent(owner)) await reload();
    };

    const run = async <T>(signature: string, command: (idempotencyKey: string) => Promise<T>, done?: string): Promise<T | null> =>
    {
        const owner = scope;
        const scopedSignature = `${owner}|${signature}`;
        update(owner, {busy: true, error: null, notice: null});
        try
        {
            const result = await command(keyFor(scopedSignature));
            keys.current.delete(scopedSignature);
            if (!guard.isCurrent(owner)) return result;
            if (done) update(owner, {notice: done});
            await reload();
            return result;
        }
        catch (caught: unknown)
        {
            if (informationRequestRefusalCode(caught) === "COMMAND_RECEIPT_FINGERPRINT_CONFLICT") {
                await forgetAndReload(owner, scopedSignature, {notice: "A previous attempt used different details. Review the latest record and try again."});
            } else if (isStaleInformationRequestCommand(caught))
            {
                await forgetAndReload(owner, scopedSignature, {notice: STALE_AUTHOR_NOTICE});
            } else if (isRepeatedInformationRequestAccessLinkCommand(caught)) {
                await forgetAndReload(owner, scopedSignature, {error: informationRequestRefusalMessage(caught, REPEATED_LINK_COMMAND_ERROR)});
            }
            else
            {
                update(owner, {error: informationRequestRefusalMessage(caught, "The change could not be made.")});
            }
            return null;
        }
        finally
        {
            update(owner, {busy: false});
        }
    };

    const shown = valueInScope(status, scope) ?? IDLE;
    return {busy: shown.busy, error: shown.error, notice: shown.notice, run};
};
