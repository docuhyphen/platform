import React, {useCallback, useEffect, useState} from 'react';
import {
    Button,
    Field,
    Input,
    MessageBar,
    MessageBarBody,
    MessageBarTitle,
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text,
} from '@fluentui/react-components';
import {DeleteRegular, PersonAddRegular} from '@fluentui/react-icons';
import {useAppAdminsTabStyles} from './AppAdminsTabStyles';
import {fetchAppAdmins, grantAppAdmin, revokeAppAdmin, searchAppAdminCandidates} from '../../../services/appRoleApi';
import {AppAdminDto, AppUserSearchResult} from '../../../services/types/dtos';
import {useAuth} from '../../../context/AuthContext.tsx';

/**
 * App Admins management screen.
 * Lists current admins, add by user lookup, revoke with last-admin guard.
 */
const AppAdminsTab: React.FC = () =>
{
    const styles = useAppAdminsTabStyles();
    const {appUser} = useAuth();
    const [admins, setAdmins] = useState<AppAdminDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [noAccess, setNoAccess] = useState(false);
    const [busy, setBusy] = useState(false);

    // Add form (global user search, not org-scoped)
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState<AppUserSearchResult[]>([]);
    const [searching, setSearching] = useState(false);
    const [selectedUserId, setSelectedUserId] = useState<string | null>(null);

    const extractErrorStatus = (err: unknown): number | undefined =>
    {
        const e = err as { status?: number; statusCode?: number; response?: { status?: number } } | null | undefined;
        return e?.status ?? e?.response?.status ?? e?.statusCode;
    };

    const extractErrorMessage = (err: unknown, fallback: string): string =>
    {
        const e = err as { errorMessage?: string; message?: string } | null | undefined;
        return e?.errorMessage || e?.message || fallback;
    };

    const looksLikeLastAdminError = (err: unknown): boolean =>
    {
        const e = err as { errorMessage?: string; message?: string } | null | undefined;
        const msg = (e?.errorMessage || e?.message || '').toString().toLowerCase();
        return msg.includes('last') && msg.includes('admin');
    };

    const loadAdmins = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        setNoAccess(false);
        try
        {
            const data = await fetchAppAdmins();
            setAdmins(data);
        }
        catch (err: unknown)
        {
            if (extractErrorStatus(err) === 403)
            {
                setNoAccess(true);
            }
            else
            {
                setError(extractErrorMessage(err, 'Failed to load admins'));
            }
        }
        finally
        {
            setLoading(false);
        }
    }, []);

    useEffect(() =>
    {
        loadAdmins();
    }, [loadAdmins]);

    // Debounced global user-search against GET /admin/roles/app-admin-candidates.
    // Triggers when the query is at least 2 chars long; clears results otherwise.
    useEffect(() =>
    {
        if (selectedUserId) return; // a candidate is already locked in
        const q = searchQuery.trim();
        if (q.length < 2)
        {
            setSearchResults([]);
            setSearching(false);
            return;
        }
        let cancelled = false;
        setSearching(true);
        const handle = window.setTimeout(() =>
        {
            searchAppAdminCandidates(q)
                .then((rows) =>
                {
                    if (cancelled) return;
                    // Hide users who are already admins so we don't offer a no-op.
                    setSearchResults(rows.filter((u) => !admins.some((a) => a.appUserId === u.id)));
                })
                .catch(() =>
                {
                    if (cancelled) return;
                    setSearchResults([]);
                })
                .finally(() =>
                {
                    if (cancelled) return;
                    setSearching(false);
                });
        }, 250);
        return () =>
        {
            cancelled = true;
            window.clearTimeout(handle);
        };
    }, [searchQuery, selectedUserId, admins]);

    const handleGrant = async () =>
    {
        if (!selectedUserId) return;
        setBusy(true);
        setError(null);
        try
        {
            await grantAppAdmin({appUserId: selectedUserId});
            setSelectedUserId(null);
            setSearchQuery('');
            await loadAdmins();
        }
        catch (err: unknown)
        {
            setError(extractErrorMessage(err, 'Failed to grant admin role'));
        }
        finally
        {
            setBusy(false);
        }
    };

    const handleRevoke = async (assignmentId: string) =>
    {
        const target = admins.find((admin) => admin.assignmentId === assignmentId);
        if (target?.appUserId && target.appUserId === appUser?.id)
        {
            setError('You cannot revoke your own app administrator access. Ask another app admin to do this.');
            return;
        }

        setBusy(true);
        setError(null);
        try
        {
            await revokeAppAdmin(assignmentId);
            await loadAdmins();
        }
        catch (err: unknown)
        {
            // Backend throws LastAppAdminException → 409 to protect the system from
            // ending up with zero admins. Surface a tailored copy instead of the raw
            // message so the user knows the system is doing it on purpose.
            if (extractErrorStatus(err) === 409 || looksLikeLastAdminError(err))
            {
                setError('You can\'t revoke the last app administrator. Grant the role to another user first.');
            }
            else
            {
                setError(extractErrorMessage(err, 'Failed to revoke admin role'));
            }
        }
        finally
        {
            setBusy(false);
        }
    };

    const formatCandidate = (u: AppUserSearchResult): string =>
    {
        const name = `${u.firstName ?? ''} ${u.lastName ?? ''}`.trim();
        return name ? `${name} (${u.email})` : u.email;
    };

    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <Text weight="semibold" size={500}>App Administrators</Text>
            </div>

            {noAccess ? (
                <MessageBar intent="info">
                    <MessageBarBody>
                        <MessageBarTitle>No access</MessageBarTitle>
                        You don't have permission to view or manage app administrators.
                        Ask an existing app admin if you need access.
                    </MessageBarBody>
                </MessageBar>
            ) : (
                <>
                    {error && (
                        <MessageBar intent="error">
                            <MessageBarBody>
                                <MessageBarTitle>Error</MessageBarTitle>
                                {error}
                            </MessageBarBody>
                        </MessageBar>
                    )}

                    {loading ? (
                        <div className={styles.loading}>
                            <Spinner label="Loading..." size="small"/>
                        </div>
                    ) : (
                <>
                    {/* Add admin */}
                    <div className={styles.addRow}>
                        <Field label="Add admin by user" style={{flex: 1}}>
                            <Input
                                size="small"
                                placeholder="Search by name or email..."
                                value={searchQuery}
                                onChange={(_e, d) =>
                                {
                                    setSearchQuery(d.value);
                                    setSelectedUserId(null);
                                }}
                            />
                        </Field>
                        <Button
                            size="small"
                            shape="circular"
                            appearance="primary"
                            icon={<PersonAddRegular/>}
                            disabled={busy || !selectedUserId}
                            onClick={handleGrant}
                        >
                            Add Admin
                        </Button>
                    </div>

                    {searchQuery.length >= 2 && !selectedUserId && (
                        <div style={{border: '1px solid var(--colorNeutralStroke1)', borderRadius: 4, maxHeight: 160, overflowY: 'auto'}}>
                            {searching && (
                                <div style={{padding: 8}}>
                                    <Spinner size="tiny" label="Searching..."/>
                                </div>
                            )}
                            {!searching && searchResults.length === 0 && (
                                <div style={{padding: 8}}>
                                    <Text size={200}>No matching users.</Text>
                                </div>
                            )}
                            {!searching && searchResults.slice(0, 10).map((u) => (
                                <Button
                                    key={u.id}
                                    size="small"
                                    appearance="subtle"
                                    style={{display: 'block', width: '100%', textAlign: 'left'}}
                                    onClick={() =>
                                    {
                                        setSelectedUserId(u.id);
                                        setSearchQuery(formatCandidate(u));
                                        setSearchResults([]);
                                    }}
                                >
                                    {formatCandidate(u)}
                                </Button>
                            ))}
                        </div>
                    )}

                    {/* Admin list */}
                    <Table size="small" className={styles.table}>
                        <TableHeader>
                            <TableRow>
                                <TableHeaderCell><Text weight="semibold">Email</Text></TableHeaderCell>
                                <TableHeaderCell><Text weight="semibold">Granted At</Text></TableHeaderCell>
                                <TableHeaderCell><Text weight="semibold">Actions</Text></TableHeaderCell>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {admins.map((admin) => (
                                <TableRow key={admin.assignmentId}>
                                    <TableCell>{admin.email || admin.appUserId || '—'}</TableCell>
                                    <TableCell>
                                        {admin.grantedAt ? new Date(admin.grantedAt).toLocaleString() : '—'}
                                    </TableCell>
                                    <TableCell>
                                        {admin.appUserId === appUser?.id ? (
                                            <Text size={200}>Current session</Text>
                                        ) : (
                                        <Button
                                            size="small"
                                            appearance="subtle"
                                            icon={<DeleteRegular/>}
                                            disabled={busy}
                                            onClick={() => handleRevoke(admin.assignmentId)}
                                        >
                                            Revoke
                                        </Button>
                                        )}
                                    </TableCell>
                                </TableRow>
                            ))}
                            {admins.length === 0 && (
                                <TableRow>
                                    <TableCell colSpan={3}>
                                        <Text>No app administrators found.</Text>
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </>
            )}
                </>
            )}
        </div>
    );
};

export default AppAdminsTab;
