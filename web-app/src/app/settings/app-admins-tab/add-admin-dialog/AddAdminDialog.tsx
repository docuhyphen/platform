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
    tokens,
} from '@fluentui/react-components';
import {PersonAddRegular} from '@fluentui/react-icons';
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
            <DialogSurface style={{minWidth: '480px', maxWidth: '600px'}}>
                <DialogBody>
                    <DialogTitle>Add App Admins</DialogTitle>
                    <DialogContent style={{display: 'flex', flexDirection: 'column', gap: '16px', marginTop: "1rem", marginBottom: "1rem"}}>

                        {error && (
                            <div style={{color: tokens.colorStatusDangerForeground1}}>
                                {error}
                            </div>
                        )}

                        <Field label="Search users by name or email">
                            <Input
                                placeholder="Type at least 2 characters..."
                                value={searchQuery}
                                onChange={(_e, d) => setSearchQuery(d.value)}
                                disabled={busy}
                            />
                        </Field>

                        {selected.size > 0 && (
                            <div style={{display: 'flex', gap: '6px', flexWrap: 'wrap'}}>
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
                            <div style={{
                                border: `1px solid ${tokens.colorNeutralStroke1}`,
                                borderRadius: tokens.borderRadiusMedium,
                                maxHeight: '260px',
                                overflowY: 'auto',
                            }}>
                                {searching && (
                                    <div style={{padding: '12px'}}>
                                        <Spinner size="tiny" label="Searching..."/>
                                    </div>
                                )}
                                {!searching && !hasResults && (
                                    <div style={{padding: '12px'}}>
                                        <Text size={200}>No matching users found.</Text>
                                    </div>
                                )}
                                {!searching && hasResults && (
                                    <Table size="small">
                                        <TableHeader>
                                            <TableRow>
                                                <TableHeaderCell style={{width: '44px'}}/>
                                                <TableHeaderCell>Name</TableHeaderCell>
                                                <TableHeaderCell>Email</TableHeaderCell>
                                            </TableRow>
                                        </TableHeader>
                                        <TableBody>
                                            {searchResults.map((u) => (
                                                <TableRow
                                                    key={u.id}
                                                    style={{cursor: 'pointer'}}
                                                    onClick={() => toggleUser(u.id)}
                                                >
                                                    <TableCell style={{width: '44px'}}>
                                                        <Checkbox
                                                            checked={selected.has(u.id)}
                                                            onChange={() => toggleUser(u.id)}
                                                        />
                                                    </TableCell>
                                                    <TableCell>
                                                        {`${u.firstName ?? ''} ${u.lastName ?? ''}`.trim() || '-'}
                                                    </TableCell>
                                                    <TableCell>
                                                        <div style={{maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'}}
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


