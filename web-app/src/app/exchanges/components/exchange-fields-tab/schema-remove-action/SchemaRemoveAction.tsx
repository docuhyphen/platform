import {useState} from 'react';
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Spinner,
    Text,
} from '@fluentui/react-components';
import {DeleteRegular} from '@fluentui/react-icons';
import {ResponseError} from '../../../../models/models';
import {unassignExchangeSchema} from '../../../../../services/fieldsService';
import {useSchemaRemoveActionStyles} from './SchemaRemoveActionStyles';

interface Props {
    exchangeId: string;
    valuesETag: string;
    onRemoved: () => void;
    onValuesMovedOn: () => void;
}

const STALE_VERSION_CODE = 'FIELDS_PRECONDITION_STALE';
const REMOVE_FAILED = 'Failed to remove the schema';
const VALUES_MOVED_ON = 'The values changed after this tab loaded, so the schema was not removed. '
    + 'The current values are loaded again; check them before removing the schema.';

const SchemaRemoveAction = ({exchangeId, valuesETag, onRemoved, onValuesMovedOn}: Props) => {
    const styles = useSchemaRemoveActionStyles();
    const [open, setOpen] = useState(false);
    const [removing, setRemoving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const dismiss = () => {
        setOpen(false);
        setError(null);
    };

    const handleRemove = async () => {
        setRemoving(true);
        setError(null);
        try {
            await unassignExchangeSchema(exchangeId, valuesETag);
            setOpen(false);
            onRemoved();
        } catch (refusal: unknown) {
            const stated = refusal as ResponseError | null;
            if (stated?.reasonCode === STALE_VERSION_CODE) {
                setError(VALUES_MOVED_ON);
                onValuesMovedOn();
            } else setError(stated?.errorMessage?.trim() || REMOVE_FAILED);
        } finally {
            setRemoving(false);
        }
    };

    return (
        <>
            <Button id="exchange-schema-remove-btn"
                    appearance="subtle"
                    shape="circular"
                    size="small"
                    icon={<DeleteRegular/>}
                    onClick={() => setOpen(true)}>
                Remove schema
            </Button>
            <Dialog open={open}
                    onOpenChange={(_, data) => {
                        if (!data.open) dismiss();
                    }}>
                <DialogSurface id="exchange-schema-remove-dialog"
                               className={styles.surface}>
                    <DialogBody id="exchange-schema-remove-body">
                        <DialogTitle id="exchange-schema-remove-title">Remove schema</DialogTitle>
                        <DialogContent id="exchange-schema-remove-content"
                                       className={styles.content}>
                            <Text id="exchange-schema-remove-summary">
                                Removing the schema also removes every value recorded against it on this
                                Exchange. Earlier recorded versions of those values stay on record.
                            </Text>
                            {error && (
                                <Text id="exchange-schema-remove-error"
                                      className={styles.errorText}>
                                    {error}
                                </Text>
                            )}
                        </DialogContent>
                        <DialogActions id="exchange-schema-remove-actions">
                            <Button id="exchange-schema-remove-confirm"
                                    appearance="primary"
                                    shape="circular"
                                    disabled={removing}
                                    icon={removing ? <Spinner id={"schema-remove-action-surface-spinner"}
                                                              size="tiny"/> : undefined}
                                    onClick={handleRemove}>
                                Remove schema and values
                            </Button>
                            <Button id="exchange-schema-remove-cancel"
                                    appearance="secondary"
                                    shape="circular"
                                    disabled={removing}
                                    onClick={dismiss}>
                                Cancel
                            </Button>
                        </DialogActions>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </>
    );
};

export default SchemaRemoveAction;
