import React, {useCallback, useEffect, useState} from 'react';
import {
    Badge,
    Button,
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
import {fetchAppAdmins, revokeAppAdmin} from '../../../services/appRoleApi';
import {AppAdminDto} from '../../../services/types/dtos';
import {useAuth} from '../../../context/AuthContext.tsx';
import AddAdminDialog from './add-admin-dialog/AddAdminDialog.tsx';

/**
 * App Admins management screen.
 * Lists current admins, add via dialog, revoke with last-admin guard.
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
    const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);

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

    const existingAdminUserIds = new Set(admins.map((a) => a.appUserId).filter(Boolean) as string[]);

    return (
        <div className={styles.container}>

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
                            <div className={styles.header}>
                                <span/>
                                <Button
                                    id={"btn-add-admin"}
                                    icon={<PersonAddRegular/>}
                                    appearance="subtle"
                                    shape="circular"
                                    onClick={() => setIsAddDialogOpen(true)}
                                >
                                    Add Admin
                                </Button>
                            </div>

                            <Table size="small" className={styles.table}>
                                <TableHeader>
                                    <TableRow>
                                        <TableHeaderCell><Text weight="semibold">Name</Text></TableHeaderCell>
                                        <TableHeaderCell><Text weight="semibold">Email</Text></TableHeaderCell>
                                        <TableHeaderCell><Text weight="semibold">Granted At</Text></TableHeaderCell>
                                        <TableHeaderCell className={styles.actionsCell}><Text weight="semibold">Actions</Text></TableHeaderCell>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {admins.map((admin) => (
                                        <TableRow key={admin.assignmentId}>
                                            <TableCell>
                                                {`${admin.firstName ?? ''} ${admin.lastName ?? ''}`.trim() || '-'}
                                            </TableCell>
                                            <TableCell>{admin.email}</TableCell>
                                            <TableCell>
                                                {admin.grantedAt ? new Date(admin.grantedAt).toLocaleString() : '-'}
                                            </TableCell>
                                            <TableCell className={styles.actionsCell}>
                                                {admin.appUserId === appUser?.id ? (
                                                    <Badge>You</Badge>
                                                ) : (
                                                    <Button
                                                        id={`btn-revoke-admin-${admin.assignmentId}`}
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
                                            <TableCell colSpan={4}>
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

            <AddAdminDialog
                isOpen={isAddDialogOpen}
                onDismiss={() => setIsAddDialogOpen(false)}
                existingAdminUserIds={existingAdminUserIds}
                onComplete={() =>
                {
                    setIsAddDialogOpen(false);
                    loadAdmins();
                }}
            />
        </div>
    );
};

export default AppAdminsTab;
