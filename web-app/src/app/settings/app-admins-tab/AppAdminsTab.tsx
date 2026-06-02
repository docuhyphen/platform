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
import {fetchAppAdmins, grantAppAdmin, revokeAppAdmin} from '../../../services/appRoleApi';
import {AppAdminDto} from '../../../services/types/dtos';
import {fetchMyOrganizationUsers} from '../../../services/organizationApi';
import {AppUserDetailedDto} from '../../models/models';

/**
 * App Admins management screen (Plan 04).
 * Lists current admins, add by user lookup, revoke with last-admin guard.
 */
const AppAdminsTab: React.FC = () =>
{
    const styles = useAppAdminsTabStyles();
    const [admins, setAdmins] = useState<AppAdminDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    // Add form
    const [searchQuery, setSearchQuery] = useState('');
    const [orgUsers, setOrgUsers] = useState<AppUserDetailedDto[]>([]);
    const [selectedUserId, setSelectedUserId] = useState<string | null>(null);

    const loadAdmins = useCallback(async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            const data = await fetchAppAdmins();
            setAdmins(data);
        }
        catch (err: any)
        {
            setError(err?.errorMessage || err?.message || 'Failed to load admins');
        }
        finally
        {
            setLoading(false);
        }
    }, []);

    useEffect(() =>
    {
        loadAdmins();
        // Pre-load org users for the picker
        fetchMyOrganizationUsers()
            .then(setOrgUsers)
            .catch(() => { /* optional, picker just won't work */});
    }, [loadAdmins]);

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
        catch (err: any)
        {
            setError(err?.errorMessage || err?.message || 'Failed to grant admin role');
        }
        finally
        {
            setBusy(false);
        }
    };

    const handleRevoke = async (assignmentId: string) =>
    {
        setBusy(true);
        setError(null);
        try
        {
            await revokeAppAdmin(assignmentId);
            await loadAdmins();
        }
        catch (err: any)
        {
            // 409 = last admin guard
            const msg = err?.errorMessage || err?.message || 'Failed to revoke admin role';
            setError(msg);
        }
        finally
        {
            setBusy(false);
        }
    };

    const filteredUsers = orgUsers.filter(
        (u) =>
            u.isActive &&
            !admins.some((a) => a.appUserId === u.id) &&
            (searchQuery.length < 2 ||
                u.email?.toLowerCase().includes(searchQuery.toLowerCase()) ||
                u.person?.firstName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
                u.person?.lastName?.toLowerCase().includes(searchQuery.toLowerCase())),
    );

    return (
        <div className={styles.container}>
            <div className={styles.header}>
                <Text weight="semibold" size={500}>App Administrators</Text>
            </div>

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

                    {searchQuery.length >= 2 && filteredUsers.length > 0 && !selectedUserId && (
                        <div style={{border: '1px solid var(--colorNeutralStroke1)', borderRadius: 4, maxHeight: 160, overflowY: 'auto'}}>
                            {filteredUsers.slice(0, 10).map((u) => (
                                <Button
                                    key={u.id}
                                    size="small"
                                    appearance="subtle"
                                    style={{display: 'block', width: '100%', textAlign: 'left'}}
                                    onClick={() =>
                                    {
                                        setSelectedUserId(u.id!);
                                        setSearchQuery(`${u.person?.firstName || ''} ${u.person?.lastName || ''} (${u.email})`.trim());
                                    }}
                                >
                                    {u.person?.firstName} {u.person?.lastName} — {u.email}
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
                                        <Button
                                            size="small"
                                            appearance="subtle"
                                            icon={<DeleteRegular/>}
                                            disabled={busy}
                                            onClick={() => handleRevoke(admin.assignmentId)}
                                        >
                                            Revoke
                                        </Button>
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
        </div>
    );
};

export default AppAdminsTab;
