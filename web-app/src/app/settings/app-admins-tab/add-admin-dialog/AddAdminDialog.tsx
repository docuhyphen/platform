import React, {useEffect, useState} from 'react';
import {
    Badge,
    Button,
    Checkbox,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Input,
    Spinner,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    Text,
} from '@fluentui/react-components';
import {PersonAddRegular} from '@fluentui/react-icons';
import {useAddAdminDialogStyles} from './AddAdminDialogStyles';
import {grantAppAdmin, searchAppAdminCandidates} from '../../../../services/appRoleApi';
import {AppUserSearchResult} from '../../../../services/types/dtos';

interface AddAdminDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    existingAdminUserIds: Set<string>;
    onComplete: () => void;
}

const AddAdminDialog: React.FC<AddAdminDialogProps> = ({isOpen, onDismiss, existingAdminUserIds, onComplete}) =>
{
    const styles = useAddAdminDialogStyles();
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState<AppUserSearchResult[]>([]);
    const [searching, setSearching] = useState(false);
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const resetForm = () =>
    {
        setSearchQuery('');
        setSearchResults([]);
        setSelected(new Set());
        setError(null);
        setSearching(false);
    };

    useEffect(() =>
    {
        if (!isOpen) resetForm();
    }, [isOpen]);

    // Debounced search
    useEffect(() =>
    {
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
                    setSearchResults(rows.filter((u) => !existingAdminUserIds.has(u.id)));
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
    }, [searchQuery, existingAdminUserIds]);

    const toggleUser = (id: string) =>
    {
        setSelected((prev) =>
        {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const handleAdd = async () =>
    {
        if (selected.size === 0) return;
        setBusy(true);
        setError(null);
        try
        {
            await Promise.all(Array.from(selected).map((id) => grantAppAdmin({appUserId: id})));
            resetForm();
            onComplete();
        }
        catch (err: unknown)
        {
            const e = err as {errorMessage?: string; message?: string} | null | undefined;
            setError(e?.errorMessage || e?.message || 'Failed to grant admin role');
        }
        finally
        {
            setBusy(false);
        }
    };

    const formatUser = (u: AppUserSearchResult): string =>
    {
        const name = `${u.firstName ?? ''} ${u.lastName ?? ''}`.trim();
        return name ? `${name} (${u.email})` : u.email;
    };

    const handleClose = () =>
    {
        resetForm();
        onDismiss();
    };

    const hasResults = searchResults.length > 0;

    return (
        <Dialog modalType="alert" open={isOpen}>
            <DialogSurface className={styles.surface}>
                <DialogBody>
                    <DialogTitle>Add App Admins</DialogTitle>
                    <DialogContent className={styles.content}>

                        {error && (
                            <div className={styles.errorText}>
                                {error}
                            </div>
                        )}

                        <Field label="Search users by name or email">
                            <Input
                                id={"input-admin-search"}
                                placeholder="Type at least 2 characters..."
                                value={searchQuery}
                                onChange={(_e, d) => setSearchQuery(d.value)}
                                disabled={busy}
                            />
                        </Field>

                        {selected.size > 0 && (
                            <div className={styles.selectedBadges}>
                                {searchResults
                                    .filter((u) => selected.has(u.id))
                                    .map((u) => (
                                        <Badge
                                            key={u.id}
                                            appearance="outline"
                                            color="brand"
                                        >
                                            {formatUser(u)}
                                        </Badge>
                                    ))}
                            </div>
                        )}

                        {searchQuery.trim().length >= 2 && (
                            <div className={styles.searchResults}>
                                {searching && (
                                    <div className={styles.searchPadding}>
                                        <Spinner size="tiny" label="Searching..."/>
                                    </div>
                                )}
                                {!searching && !hasResults && (
                                    <div className={styles.searchPadding}>
                                        <Text size={200}>No matching users found.</Text>
                                    </div>
                                )}
                                {!searching && hasResults && (
                                    <Table size="small">
                                        <TableHeader>
                                            <TableRow>
                                                <TableHeaderCell className={styles.checkboxCell}/>
                                                <TableHeaderCell>Name</TableHeaderCell>
                                                <TableHeaderCell>Email</TableHeaderCell>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {searchResults.map((u) => (
                                                <TableRow
                                                    key={u.id}
                                                    className={styles.clickableRow}
                                                    onClick={() => toggleUser(u.id)}
                                                >
                                                    <TableCell className={styles.checkboxCell}>
                                                        <Checkbox
                                                            id={`checkbox-admin-user-${u.id}`}
                                                            checked={selected.has(u.id)}
                                                            onChange={() => toggleUser(u.id)}
                                                        />
                                                    </TableCell>
                                                    <TableCell>
                                                        {`${u.firstName ?? ''} ${u.lastName ?? ''}`.trim() || '-'}
                                                    </TableCell>
                                                    <TableCell>
                                                        <div className={styles.emailCell}
                                                             title={u.email}>
                                                            {u.email}
                                                        </div>
                                                    </TableCell>
                                                </TableRow>
                                            ))}
                                        </TableBody>
                                    </Table>
                                )}
                            </div>
                        )}

                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        id={"btn-dialog-add-admin"}
                        appearance="primary"
                        shape="circular"
                        icon={busy ? <Spinner size="tiny"/> : <PersonAddRegular/>}
                        disabled={busy || selected.size === 0}
                        onClick={handleAdd}
                    >
                        {selected.size > 1 ? `Add ${selected.size} Admins` : 'Add Admin'}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id={"btn-dialog-cancel"}
                            appearance="secondary"
                            shape="circular"
                            disabled={busy}
                            onClick={handleClose}
                        >
                            Cancel
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
};

export default AddAdminDialog;


