import React, {useEffect, useState} from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Spinner,
} from '@fluentui/react-components';
import {PersonAddRegular} from '@fluentui/react-icons';
import {useAddAdminDialogStyles} from './AddAdminDialogStyles';
import {grantAppAdmin, searchAppAdminCandidates} from '../../../../services/appRoleApi';
import {AppUserSearchResult} from '../../../../services/types/dtos';
import MultiPersonPicker from '../../../components/person-picker/multi-person-picker/MultiPersonPicker.tsx';
import {PersonPickerItem} from '../../../components/person-picker/personPickerTypes.ts';

const toPersonPickerItem = (user: AppUserSearchResult): PersonPickerItem => ({
    id: user.id,
    email: user.email,
    firstName: user.firstName,
    lastName: user.lastName,
    avatarUrl: user.avatarUrl,
});

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
    const [selectedUsers, setSelectedUsers] = useState<AppUserSearchResult[]>([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const resetForm = () =>
    {
        setSearchQuery('');
        setSearchResults([]);
        setSelectedUsers([]);
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

    const onSelectionChange = (selectedIds: string[]) =>
    {
        const users = [...selectedUsers, ...searchResults];
        setSelectedUsers(selectedIds
            .map(id => users.find(user => user.id === id))
            .filter((user): user is AppUserSearchResult => !!user));
    };

    const handleAdd = async () =>
    {
        if (selectedUsers.length === 0) return;
        setBusy(true);
        setError(null);
        try
        {
            await Promise.all(selectedUsers.map(user => grantAppAdmin({appUserId: user.id})));
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

    const handleClose = () =>
    {
        resetForm();
        onDismiss();
    };

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
                            <MultiPersonPicker
                                id="input-admin-search"
                                people={searchResults.map(toPersonPickerItem)}
                                selectedPeople={selectedUsers.map(toPersonPickerItem)}
                                onSelectionChange={onSelectionChange}
                                query={searchQuery}
                                onQueryChange={setSearchQuery}
                                placeholder="Type at least 2 characters"
                                disabled={busy}
                                noResultsText="No matching users found"
                            />
                        </Field>

                        {searching && <Spinner size="tiny" label="Searching..."/>}

                    </DialogContent>
                </DialogBody>
                <DialogActions>
                    <Button
                        id={"btn-dialog-add-admin"}
                        appearance="primary"
                        shape="circular"
                        icon={busy ? <Spinner size="tiny"/> : <PersonAddRegular/>}
                        disabled={busy || selectedUsers.length === 0}
                        onClick={handleAdd}
                    >
                        {selectedUsers.length > 1 ? `Add ${selectedUsers.length} Admins` : 'Add Admin'}
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


