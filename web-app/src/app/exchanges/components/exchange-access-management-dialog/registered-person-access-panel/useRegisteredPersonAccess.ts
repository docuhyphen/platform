import {useEffect, useMemo, useState} from 'react';
import {grantExchangeAccess} from '../../../../../services/exchangeApi.ts';
import {ExchangeShareRoleName} from '../../../../../services/types/roles.ts';
import {searchContacts, UserContactDto} from '../../../../../services/personalContactsApi.ts';
import {
    ConstraintTag,
    constraintsFromTags,
    isConstrainedRole,
} from './registeredPersonConstraints.ts';

export const useRegisteredPersonAccess = (exchangeId: string, onAdded: () => void) =>
{
    const [email, setEmail] = useState('');
    const [role, setRole] = useState<ExchangeShareRoleName>(ExchangeShareRoleName.VIEWER);
    const [constraints, setConstraints] = useState<ConstraintTag[]>([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [contacts, setContacts] = useState<UserContactDto[]>([]);
    const [searching, setSearching] = useState(false);
    const canAddConstraints = useMemo(() => isConstrainedRole(role), [role]);

    useEffect(() =>
    {
        const query = email.trim();
        if (query.length < 2)
        {
            setContacts([]);
            setSearching(false);
            return;
        }
        let cancelled = false;
        setSearching(true);
        const handle = window.setTimeout(() =>
        {
            searchContacts(query, 10)
                .then(results => !cancelled && setContacts(results))
                .catch(() => !cancelled && setContacts([]))
                .finally(() => !cancelled && setSearching(false));
        }, 250);
        return () =>
        {
            cancelled = true;
            window.clearTimeout(handle);
        };
    }, [email]);

    const add = async () =>
    {
        if (!email.trim() || busy) return;
        setBusy(true);
        setError(null);
        try
        {
            await grantExchangeAccess(exchangeId, {
                principalKind: 'USER',
                principalId: email.trim(),
                roleName: role,
                constraintsJson: canAddConstraints
                    ? JSON.stringify(constraintsFromTags(constraints))
                    : undefined,
            });
            onAdded();
        }
        catch (caught: unknown)
        {
            setError(caught instanceof Error ? caught.message : String(caught));
        }
        finally
        {
            setBusy(false);
        }
    };

    return {
        email, setEmail, role, setRole, constraints, setConstraints, busy, error,
        contacts, searching, canAddConstraints, add,
    };
};
