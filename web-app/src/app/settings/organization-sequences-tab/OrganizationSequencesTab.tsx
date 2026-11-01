import {useAuth} from '../../../context/AuthContext';
import {Button, Spinner, Text} from '@fluentui/react-components';
import {AddRegular} from '@fluentui/react-icons';
import {Capability} from '../../models/models';
import {useOrganizationSequencesTabStyles} from './OrganizationSequencesTabStyles';
import ViewModeToggle from '../../components/view-mode-toggle/ViewModeToggle.tsx';
import {useViewModePreference} from '../components/useViewModePreference.ts';
import {useOrganizationSequences} from './useOrganizationSequences.ts';
import {useSequenceEditor} from './useSequenceEditor.ts';
import SequenceCard from './sequence-card/SequenceCard.tsx';
import SequenceTable from './sequence-table/SequenceTable.tsx';
import SequenceDrawer from './sequence-drawer/SequenceDrawer.tsx';

const OrganizationSequencesTab = () =>
{
    const styles = useOrganizationSequencesTabStyles();
    const {appUserPersonOrganization, hasCapability} = useAuth();
    const canManage =
        appUserPersonOrganization?.isActive &&
        hasCapability(Capability.ORG_POLICY_MANAGE);
    const {viewMode, changeViewMode} = useViewModePreference('sequencesView');
    const list = useOrganizationSequences();
    const editor = useSequenceEditor(list.load);
    const hasSequences = !list.loading && !list.error && list.sequences.length > 0;

    return (
        <>
            <div id={"organization-sequences-tab-div-1"}
                 className={styles.container}>
                <div id={"organization-sequences-tab-div-2"}
                     className={styles.header}>
                    <Text
                        id={"organization-sequences-tab-text-1"}
                        size={500}
                        weight="semibold"
                    >
                        Sequences
                    </Text>
                    {canManage && (
                        <Button
                            id={"button-create-sequence"}
                            appearance="subtle"
                            shape={"circular"}
                            icon={<AddRegular/>}
                            onClick={editor.openCreate}
                        >
                            Create Sequence
                        </Button>
                    )}
                </div>
                <Text
                    id={"organization-sequences-tab-text-2"}
                    size={300}
                    className={styles.descriptionText}
                >
                    Auto-incrementing counters. Use <code id={"organization-sequences-tab-code-1"}>{'{{SEQ:KEY}}'}</code> in blueprint and Exchange names.
                </Text>

                <div id={"organization-sequences-tab-div-3"}
                     className={styles.toolbar}>
                    <ViewModeToggle value={viewMode}
                                    onChange={changeViewMode}/>
                </div>

                {list.loading && <Spinner id={"organization-sequences-tab-spinner-1"}
                                          size="small"
                                          label="Loading…"/>}
                {!list.loading && list.error && (
                    <Text id={"organization-sequences-tab-text-3"}
                          className={styles.errorText}>{list.error}</Text>
                )}
                {!list.loading && !list.error && list.sequences.length === 0 && (
                    <Text id={"organization-sequences-tab-text-4"}
                          className={styles.emptyText}>No sequences yet.</Text>
                )}
                {hasSequences && viewMode === 'cards' && list.sequences.map(seq => (
                    <SequenceCard key={seq.id}
                                  sequence={seq}
                                  canManage={!!canManage}
                                  onEdit={editor.openEdit}
                                  onReset={list.resetCounter}
                                  onDelete={list.remove}/>
                ))}
                {hasSequences && viewMode === 'table' && (
                    <SequenceTable sequences={list.sequences}
                                   canManage={!!canManage}
                                   onEdit={editor.openEdit}
                                   onReset={list.resetCounter}
                                   onDelete={list.remove}/>
                )}
            </div>

            <SequenceDrawer editor={editor}/>
        </>
    );
};

export default OrganizationSequencesTab;
